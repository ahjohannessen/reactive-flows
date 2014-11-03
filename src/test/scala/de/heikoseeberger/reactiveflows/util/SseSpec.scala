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

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.{ Matchers, WordSpec }

class SseSpec
    extends WordSpec
    with Matchers
    with TypeCheckedTripleEquals {

  "Calling messageToByteString" should {

    "return a single data line for single line message" in {
      val message = Sse.Message("line")
      Sse.messageToByteString(message).utf8String should ===("data:line\n\n")
    }

    "return multiple data lines for a multi-line message" in {
      val message = Sse.Message("line1\nline2\n")
      Sse.messageToByteString(message).utf8String should ===("data:line1\ndata:line2\ndata:\n\n")
    }

    "return multiple data lines and an event line for a multi-line message with a defined event" in {
      val message = Sse.Message("line1\nline2", Some("evt"))
      Sse.messageToByteString(message).utf8String should ===("event:evt\ndata:line1\ndata:line2\n\n")
    }
  }
}
