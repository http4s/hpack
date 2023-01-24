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
package org.http4s.hpack;

import org.http4s.hpack.HpackUtil.ISO_8859_1;
import org.http4s.hpack.HpackUtil.requireNonNull;

private[http4s] object HeaderField {
  // Section 4.1. Calculating Table Size
  // The additional 32 octets account for an estimated
  // overhead associated with the structure.
  val HEADER_ENTRY_OVERHEAD = 32;

  def sizeOf(name: Array[Byte], value: Array[Byte]): Int =
    return name.length + value.length + HEADER_ENTRY_OVERHEAD;
}

private[http4s] class HeaderField(val name: Array[Byte], val value: Array[Byte])
    extends Comparable[HeaderField] {
  requireNonNull(name)
  requireNonNull(value)

  // This constructor can only be used if name and value are ISO-8859-1 encoded.
  def this(name: String, value: String) =
    this(name.getBytes(ISO_8859_1), value.getBytes(ISO_8859_1));

  def size(): Int =
    return name.length + value.length + HeaderField.HEADER_ENTRY_OVERHEAD;

  override def compareTo(anotherHeaderField: HeaderField): Int = {
    var ret = compareTo(name, anotherHeaderField.name);
    if (ret == 0) {
      ret = compareTo(value, anotherHeaderField.value);
    }
    return ret;
  }

  private def compareTo(s1: Array[Byte], s2: Array[Byte]): Int = {
    val len1 = s1.length;
    val len2 = s2.length;
    val lim = Math.min(len1, len2);

    var k = 0;
    while (k < lim) {
      val b1 = s1(k);
      val b2 = s2(k);
      if (b1 != b2) {
        return b1 - b2;
      }
      k += 1;
    }
    return len1 - len2;
  }

  override def equals(obj: Any): Boolean = {
    if (obj.isInstanceOf[AnyRef] && (obj.asInstanceOf[AnyRef] eq this)) {
      return true;
    }
    if (!obj.isInstanceOf[HeaderField]) {
      return false;
    }
    val other = obj.asInstanceOf[HeaderField];
    val nameEquals = HpackUtil.equals(name, other.name);
    val valueEquals = HpackUtil.equals(value, other.value);
    return nameEquals && valueEquals;
  }

  override def toString(): String = {
    val nameString = new String(name);
    val valueString = new String(value);
    return nameString + ": " + valueString;
  }
}
