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
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.hpack

import java.io.IOException

/** Extracted from org/apache/commons/codec/binary/Hex.java
  * Copyright Apache Software Foundation
  */
object Hex {

  /** Used to build output as Hex
    */
  private val DIGITS_LOWER =
    Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
  private val DIGITS_UPPER =
    Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

  /** Converts an array of characters representing hexadecimal values into an array of bytes of those same values. The
    * returned array will be half the length of the passed array, as it takes two characters to represent any given
    * byte. An exception is thrown if the passed char array has an odd number of elements.
    *
    * @param data
    * An array of characters containing hexadecimal digits
    * @return A byte array containing binary data decoded from the supplied char array.
    * @throws IOException
    * Thrown if an odd number or illegal of characters is supplied
    */
  @throws[IOException]
  def decodeHex(data: Array[Char]): Array[Byte] = {
    val len = data.length
    if ((len & 0x01) != 0) throw new IOException("Odd number of characters.")
    val out = new Array[Byte](len >> 1)
    // two characters form the hex value.
    var i = 0
    var j = 0
    while (j < len) {
      var f = toDigit(data(j), j) << 4
      j += 1
      f = f | toDigit(data(j), j)
      j += 1
      out(i) = (f & 0xff).toByte

      i += 1
    }
    out
  }

  /** Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in order.
    * The returned array will be double the length of the passed array, as it takes two characters to represent any
    * given byte.
    *
    * @param data
    * a byte[] to convert to Hex characters
    * @return A char[] containing hexadecimal characters
    */
  def encodeHex(data: Array[Byte]): Array[Char] = encodeHex(data, true)

  /** Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in order.
    * The returned array will be double the length of the passed array, as it takes two characters to represent any
    * given byte.
    *
    * @param data
    * a byte[] to convert to Hex characters
    * @param toLowerCase
    * <code>true</code> converts to lowercase, <code>false</code> to uppercase
    * @return A char[] containing hexadecimal characters
    * @since 1.4
    */
  def encodeHex(data: Array[Byte], toLowerCase: Boolean): Array[Char] = encodeHex(
    data,
    if (toLowerCase) DIGITS_LOWER
    else DIGITS_UPPER,
  )

  /** Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in order.
    * The returned array will be double the length of the passed array, as it takes two characters to represent any
    * given byte.
    *
    * @param data
    * a byte[] to convert to Hex characters
    * @param toDigits
    * the output alphabet
    * @return A char[] containing hexadecimal characters
    * @since 1.4
    */
  protected def encodeHex(data: Array[Byte], toDigits: Array[Char]): Array[Char] = {
    val l = data.length
    val out = new Array[Char](l << 1)
    var i = 0
    var j = 0
    while (i < l) {
      out {
        j += 1; j - 1
      } = toDigits((0xf0 & data(i)) >>> 4)
      out {
        j += 1; j - 1
      } = toDigits(0x0f & data(i))

      i += 1
    }
    out
  }

  /** Converts an array of bytes into a String representing the hexadecimal values of each byte in order. The returned
    * String will be double the length of the passed array, as it takes two characters to represent any given byte.
    *
    * @param data
    * a byte[] to convert to Hex characters
    * @return A String containing hexadecimal characters
    * @since 1.4
    */
  def encodeHexString(data: Array[Byte]) = new String(encodeHex(data))

  /** Converts a hexadecimal character to an integer.
    *
    * @param ch
    * A character to convert to an integer digit
    * @param index
    * The index of the character in the source
    * @return An integer
    * @throws IOException
    * Thrown if ch is an illegal hex character
    */
  @throws[IOException]
  protected def toDigit(ch: Char, index: Int): Int = {
    val digit = Character.digit(ch, 16)
    if (digit == -1)
      throw new IOException("Illegal hexadecimal character " + ch + " at index " + index)
    digit
  }
}
