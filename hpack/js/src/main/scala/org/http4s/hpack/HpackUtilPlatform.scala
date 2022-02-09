/*
 * Copyright 2022 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.http4s.hpack

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.Int8Array
import scala.annotation.nowarn

private[hpack] class HpackUtilPlatform {

  // the property holding the Uint8Array backing the Array[Byte] implementation
  // this lets us avoid having to copy it via the provided APIs
  private[this] val array0Property =
    js.Object.getOwnPropertyNames((new Array[Byte](0)).asInstanceOf[js.Object])(0)

  /** A string compare that doesn't leak timing information.
    */
  @inline final def equals(s1: Array[Byte], s2: Array[Byte]): Boolean =
    if (s1.length != s2.length) false
    else {
      val a = s1.asInstanceOf[js.Dynamic].selectDynamic(array0Property).asInstanceOf[Int8Array]
      val b = s2.asInstanceOf[js.Dynamic].selectDynamic(array0Property).asInstanceOf[Int8Array]
      crypto.timingSafeEqual(a, b)
    }

}

private[hpack] object crypto {
  @nowarn
  @js.native
  @JSImport("crypto", "timingSafeEqual")
  def timingSafeEqual(a: Int8Array, b: Int8Array): Boolean = js.native
}
