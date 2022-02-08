package org.http4s.hpack

import io.circe.parser.decode

import scala.scalajs.js

trait TestCasePlatform {

  lazy val fs = js.Dynamic.global.require("fs")

  def load(fileName: String): TestCase = {
    val path = s"hpack/shared/src/test/resources/$fileName"
    decode[TestCase](
      fs.readFileSync(path, js.Dynamic.literal(encoding = "utf8"))
        .asInstanceOf[String]
    ).fold(throw _, identity(_))
  }

}
