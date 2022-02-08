package org.http4s.hpack

import io.circe.jawn

import java.io.File

trait TestCasePlatform {

  def load(fileName: String): TestCase =
    jawn
      .decodeFile[TestCase](new File(getClass.getResource(fileName).toURI()))
      .fold(throw _, identity(_))

}
