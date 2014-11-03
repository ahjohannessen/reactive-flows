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
package util

import akka.http.model.{ ContentType, HttpCharsets, HttpEntity, HttpResponse, MediaType }
import akka.stream.scaladsl.Source
import akka.util.ByteString

object Sse {

  case class Message(data: String, event: Option[String] = None)

  val `text/event-stream`: ContentType =
    ContentType(MediaType.custom("text", "event-stream"), HttpCharsets.`UTF-8`)

  def response(messages: Source[Message]): HttpResponse = {
    val entity = HttpEntity.CloseDelimited(`text/event-stream`, messages.map(messageToByteString))
    HttpResponse(entity = entity)
  }

  def response[A](messages: Source[A], toMessage: A => Message): HttpResponse = {
    val entity = HttpEntity.CloseDelimited(`text/event-stream`, messages.map(toMessage.andThen(messageToByteString)))
    HttpResponse(entity = entity)
  }

  def messageToByteString(message: Message): ByteString = {
    val data = message.data.split("\n", -1).map(line => s"data:$line").mkString("", "\n", "\n")
    ByteString(message.event.foldLeft(s"$data\n")((data, event) => s"event:$event\n$data"))
  }
}
