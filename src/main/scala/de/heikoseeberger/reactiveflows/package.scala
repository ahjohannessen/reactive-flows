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

package de.heikoseeberger

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import spray.json.{ JsString, JsValue, RootJsonFormat }

package object reactiveflows {

  type Traversable[+A] = scala.collection.immutable.Traversable[A]

  type Iterable[+A] = scala.collection.immutable.Iterable[A]

  type Seq[+A] = scala.collection.immutable.Seq[A]

  type IndexedSeq[+A] = scala.collection.immutable.IndexedSeq[A]

  implicit object LocalDateTimeFormat extends RootJsonFormat[LocalDateTime] {

    override def write(localDateTime: LocalDateTime): JsValue =
      JsString(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(localDateTime))

    override def read(json: JsValue): LocalDateTime =
      json match {
        case JsString(s) => LocalDateTime.from(DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(s))
        case _           => sys.error(s"JsString expected: $json")
      }
  }
}
