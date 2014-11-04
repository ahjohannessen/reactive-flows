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

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionKey }
import akka.cluster.Cluster
import akka.contrib.datareplication.{ DataReplication, ORSet, Replicator }
import akka.contrib.pattern.{ DistributedPubSubExtension, DistributedPubSubMediator }
import akka.pattern.ask
import akka.util.Timeout
import java.net.URLEncoder
import scala.concurrent.{ ExecutionContext, Future }
import spray.json.DefaultJsonProtocol

object FlowRepository extends ExtensionKey[FlowRepository] {

  sealed trait Event

  case class FlowData(name: String, label: String)
  object FlowData extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

  case class AddFlowRequest(label: String)
  object AddFlowRequest extends DefaultJsonProtocol { implicit val format = jsonFormat1(apply) }
  sealed trait AddFlowReponse
  case class FlowAdded(flowData: FlowData) extends AddFlowReponse with Event
  case class FlowAlreadyRegistered(label: String) extends AddFlowReponse

  sealed trait RemoveFlowReponse
  case class FlowRemoved(name: String) extends RemoveFlowReponse with Event
  case class UnknownFlow(name: String) extends RemoveFlowReponse

  val replicatorKey: String = "flow-data"

  val eventKey: String = "flow-repository-events"
}

class FlowRepository(system: ExtendedActorSystem) extends Extension {

  import FlowRepository._

  private implicit val node = Cluster(system)

  private val replicator = DataReplication(system).replicator

  private val mediator = DistributedPubSubExtension(system).mediator

  def findAll(implicit timeout: Timeout, ec: ExecutionContext): Future[Set[FlowData]] = {
    replicator
      .ask(Replicator.Get(replicatorKey))
      .mapTo[Replicator.GetResponse]
      .flatMap {
        case Replicator.GetSuccess(`replicatorKey`, flows: ORSet, _) =>
          Future.successful(flows.value.asInstanceOf[Set[FlowData]])
        case Replicator.NotFound(`replicatorKey`, _) =>
          Future.successful(Set.empty[FlowData])
        case other =>
          Future.failed(new Exception(s"Error getting all flow data: $other"))
      }
  }

  def find(flowName: String)(implicit timeout: Timeout, ec: ExecutionContext): Future[Option[FlowData]] = {
    replicator
      .ask(Replicator.Get(replicatorKey))
      .mapTo[Replicator.GetResponse]
      .flatMap {
        case Replicator.GetSuccess(`replicatorKey`, flows: ORSet, _) =>
          Future.successful(flows.value.asInstanceOf[Set[FlowData]].find(_.name == flowName))
        case Replicator.NotFound(`replicatorKey`, _) =>
          Future.successful(None)
        case other =>
          Future.failed(new Exception(s"Error getting flow data for [$flowName]: $other"))
      }
  }

  def add(request: AddFlowRequest)(implicit timeout: Timeout, ec: ExecutionContext): Future[AddFlowReponse] = {
    def add(flowData: FlowData) =
      replicator
        .ask(Replicator.Update(replicatorKey, ORSet.empty)(_ + flowData))
        .mapTo[Replicator.UpdateResponse]
        .flatMap {
          case Replicator.UpdateSuccess(`replicatorKey`, _) =>
            val flowAdded = FlowAdded(flowData)
            mediator ! DistributedPubSubMediator.Publish(eventKey, flowAdded)
            Future.successful(flowAdded)
          case other =>
            Future.failed(new Exception(s"Error adding flow [${flowData.name}}]: $other"))
        }
    findAll.flatMap { flowDatas =>
      val name = URLEncoder.encode(request.label.toLowerCase, "UTF-8")
      if (flowDatas.exists(_.name == name))
        Future.successful(FlowAlreadyRegistered(request.label))
      else
        add(FlowData(name, request.label))
    }
  }

  def remove(name: String)(implicit timeout: Timeout, ec: ExecutionContext): Future[RemoveFlowReponse] = {
    def remove(flowData: FlowData) =
      replicator
        .ask(Replicator.Update(replicatorKey, ORSet.empty)(_ - flowData))
        .mapTo[Replicator.UpdateResponse]
        .flatMap {
          case Replicator.UpdateSuccess(`replicatorKey`, _) =>
            val flowRemoved = FlowRemoved(name)
            mediator ! DistributedPubSubMediator.Publish(eventKey, flowRemoved)
            Future.successful(flowRemoved)
          case other =>
            Future.failed(new Exception(s"Error removing flow [$name]: $other"))
        }
    findAll.flatMap { flowDatas =>
      flowDatas.find(_.name == name) match {
        case None           => Future.successful(UnknownFlow(name))
        case Some(flowData) => remove(flowData)
      }
    }
  }
}
