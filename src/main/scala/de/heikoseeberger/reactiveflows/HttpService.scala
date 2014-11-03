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
import akka.http.server.{ Route, ScalaRoutingDSL }
import akka.io.IO
import akka.pattern.{ ask, pipe }
import akka.stream.FlowMaterializer
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source
import akka.util.Timeout
import de.heikoseeberger.reactiveflows.util.Sse
import scala.concurrent.duration.DurationInt
import spray.json.{ PrettyPrinter, jsonWriter }

object HttpService {

  private case object Shutdown

  def props(interface: String, port: Int, bindTimeout: Timeout): Props =
    Props(new HttpService(interface, port, bindTimeout))

  def flowEventToSseMessage(event: Flow.Event): Sse.Message =
    event match {
      case messageAdded: Flow.MessageAdded =>
        val data = PrettyPrinter(jsonWriter[Flow.MessageAdded].write(messageAdded))
        Sse.Message(data, Some("added"))
    }
}

class HttpService(interface: String, port: Int, bindTimeout: Timeout)
    extends Actor
    with ActorLogging
    with ScalaRoutingDSL {

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
    assets ~ shutdown ~ messages

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

  protected def createFlowEventPublisher(): ActorRef =
    context.actorOf(FlowEventPublisher.props)
}
