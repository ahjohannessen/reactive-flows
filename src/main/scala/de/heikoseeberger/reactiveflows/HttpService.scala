/*
 * Copyright 2014 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.heikoseeberger.reactiveflows

import akka.actor.{ Actor, ActorLogging, ActorRef, Props, Status }
import akka.http.Http
import akka.http.model.StatusCodes
import akka.http.server.{ Route, ScalaRoutingDSL }
import akka.io.IO
import akka.pattern.{ ask, pipe }
import akka.stream.FlowMaterializer
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source
import akka.util.Timeout
import de.heikoseeberger.reactiveflows.util.{ MarshallingDirectives, SprayJsonSupport, Sse }
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import spray.json.{ DefaultJsonProtocol, PrettyPrinter, jsonWriter }

object HttpService {

  private case object Shutdown

  def props(interface: String, port: Int, bindTimeout: Timeout, askTimeout: Timeout): Props =
    Props(new HttpService(interface, port, bindTimeout)(askTimeout))

  def flowEventToSseMessage(event: Flow.Event): Sse.Message =
    event match {
      case messageAdded: Flow.MessageAdded =>
        val data = PrettyPrinter(jsonWriter[Flow.MessageAdded].write(messageAdded))
        Sse.Message(data, Some("added"))
    }

  def flowRepositoryEventToSseMessage(event: FlowRepository.Event): Sse.Message =
    event match {
      case FlowRepository.FlowAdded(flowData) =>
        Sse.Message(PrettyPrinter(jsonWriter[FlowRepository.FlowData].write(flowData)), Some("added"))
      case FlowRepository.FlowRemoved(name) =>
        Sse.Message(name, Some("removed"))
    }
}

class HttpService(interface: String, port: Int, bindTimeout: Timeout)(implicit askTimeout: Timeout)
    extends Actor
    with ActorLogging
    with ScalaRoutingDSL
    with SprayJsonSupport
    with MarshallingDirectives
    with DefaultJsonProtocol {

  import context.dispatcher
  import de.heikoseeberger.reactiveflows.HttpService._

  private implicit val materializer = FlowMaterializer()

  IO(Http)(context.system)
    .ask(Http.Bind(interface, port))(bindTimeout)
    .mapTo[Http.ServerBinding]
    .pipeTo(self)

  override def receive: Receive = {
    case serverBinding: Http.ServerBinding =>
      log.info(s"Listening on $interface:$port")
      log.info(s"To shutdown, send GET request to http://$interface:$port/shutdown")
      handleConnections(serverBinding).withRoute(route)

    case Status.Failure(cause) =>
      log.error(cause, s"Could not bind to $interface:$port!")
      throw cause

    case Shutdown =>
      context.system.shutdown()
  }

  private def route: Route =
    assets ~ shutdown ~ messages ~ flows

  private def assets: Route =
    // format: OFF
    path("") {
      getFromResource("web/index.html")
    } ~
    getFromResourceDirectory("web") // format: ON

  private def shutdown: Route =
    path("shutdown") {
      get {
        complete {
          context.system.scheduler.scheduleOnce(500 millis, self, Shutdown)
          log.info("Shutting down now ...")
          "Shutting down now ..."
        }
      }
    }

  private def messages: Route =
    path("messages") {
      get {
        complete {
          val source = Source(ActorPublisher[Flow.Event](createFlowEventPublisher()))
          Sse.response(source, flowEventToSseMessage)
        }
      }
    }

  private def flows: Route =
    // format: OFF
    pathPrefix("flows") {
      path(Segment / "messages") { flowName =>
        get {
          complete {
            FlowRepository(context.system)
              .find(flowName)
              .flatMap { flowData =>
                if (flowData.isEmpty)
                  Future.successful(StatusCodes.NotFound -> Nil)
                else
                  Flows(context.system)
                    .getMessages(flowName)
                    .map(messages => StatusCodes.OK -> messages)
              }
          }
        } ~
        post {
          entity(as[MessageRequest]) { eventualMessageRequest =>
            complete {
              FlowRepository(context.system)
                .find(flowName)
                .flatMap { flowData =>
                  if (flowData.isEmpty)
                    Future.successful(StatusCodes.NotFound)
                  else
                    Flows(context.system)
                      .addMessage(flowName, eventualMessageRequest.map(_.text))
                      .map(_ => StatusCodes.Created)
                }
            }
          }
        }
      } ~
      path(Segment) { flowName =>
        delete {
          complete {
            FlowRepository(context.system)
              .remove(flowName)
              .mapTo[FlowRepository.RemoveFlowReponse]
              .map {
                case _: FlowRepository.FlowRemoved => StatusCodes.NoContent
                case _: FlowRepository.UnknownFlow => StatusCodes.NotFound
              }
          }
        }
      } ~
      get {
        parameter("events") { _ =>
          complete {
            val source =
              Source(ActorPublisher[FlowRepository.Event](createFlowRepositoryEventPublisher()))
            Sse.response(source, flowRepositoryEventToSseMessage)
          }
        } ~
        complete {
          FlowRepository(context.system).findAll
        }
      } ~
      post {
        entity(as[FlowRepository.AddFlowRequest]) { eventualFlowRequest =>
          complete {
            eventualFlowRequest.flatMap { flowData =>
              FlowRepository(context.system)
                .add(flowData)
                .mapTo[FlowRepository.AddFlowReponse]
                .map {
                  case _: FlowRepository.FlowAdded             => StatusCodes.Created
                  case _: FlowRepository.FlowAlreadyRegistered => StatusCodes.Conflict
                }
            }
          }
        }
      }
    } // format: ON

  protected def createFlowEventPublisher(): ActorRef =
    context.actorOf(FlowEventPublisher.props)

  protected def createFlowRepositoryEventPublisher(): ActorRef =
    context.actorOf(FlowRepositoryEventPublisher.props)
}
