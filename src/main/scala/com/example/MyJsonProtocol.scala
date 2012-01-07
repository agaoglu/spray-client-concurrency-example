package com.example

import cc.spray.json._

trait MyJsonProtocol extends DefaultJsonProtocol {
  implicit val docFormat = jsonFormat(Doc, "_id", "_rev", "page")
}

object MyJsonProtocol extends MyJsonProtocol
