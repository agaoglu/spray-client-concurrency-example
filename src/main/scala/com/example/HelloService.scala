package com.example

import akka.actor.Actor
import cc.spray._
import cc.spray.http._
import cc.spray.client._
import cc.spray.json._
import cc.spray.typeconversion._

import HttpHeaders._
import MediaTypes._
import StatusCodes._
import cc.spray.typeconversion.DefaultUnmarshallers._
import cc.spray.typeconversion.SprayJsonSupport._
import MyJsonProtocol._

trait HelloService extends Directives {
  
  val helloService = {
    path("load") {
      content(as[String]) { urls => ctx =>
        val web = Actor.registry.actorsFor[Web].head
        // send each line of url to Web actor
        urls.split("\n").foreach(url => web ! url)
        ctx.complete("Started")
      }
    }
  }
  
}


case class Update(id: String, value: String, expandLines: Boolean = true) // Message to send to Db actor
case class Doc(_id: String, _rev: Option[String], page: String) // Document to save to couch 

class Web extends Actor {
  def receive = {
    case urlString: String =>
      println("Starting process for " + urlString)
      val url = new java.net.URL(urlString)
      val port = if (url.getPort() > 0) url.getPort() else 80
      // create a new HttpConduit for each url, because hosts probably differ
      val conduit = new HttpConduit(url.getHost(), port) {
        val fetch = ( // Get the response as String
          simpleRequest
          ~> sendReceive
          ~> unmarshal[String]
        )
      }
      val response = conduit.fetch(Get(url.getPath()))
      response onComplete { f =>
        f.value.get match {
          case Right(content: String) => // we have a response, send content to db
            Actor.registry.actorsFor[Db].head ! Update(urlString, content)
          case ex => // Response failed; timeout, error anything
            println("Response was not read: " + ex)
        }
        conduit.close() // close it after completion
      }
  }
}


class Db extends Actor {
  
  val data = new HttpConduit("localhost", 5984) { // DB connection, never closed
    // default unmarshal rejects unmarshallable responses, this takes things lightly
    def unmarshalOrDont[T :Unmarshaller] = transformResponse { response: HttpResponse =>
      unmarshaller[T].apply(response.content) match {
        case Right(value) => value
        case Left(error) => response
      }
    }
    // couch replys application/json only if requester 'Accept's it, so we'll defina the header
    val read = ( simpleRequest ~> addHeaders(Accept(`application/json`)) ~> sendReceive ~> unmarshalOrDont[Doc] )
    val update = ( simpleRequest[Doc] ~> sendReceive )
  }
  
  def urlencode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
  
  def receive = {
    case Update(id, value, expandLines) => data.read(Get("/db/" + urlencode(id))) onComplete { // Try reading first
      _.value.get match {
        case Right(d: Doc) => // couch returned existing data, update it
          data.update(Put("/db/" + urlencode(id), Doc(d._id, d._rev, value))) // don't care if it succeeded
          if (expandLines) { // expandLines defined, we will save each line to couch
            val lines = value.split("\n")
            lines.zip(lines.indices).foreach( zipped =>
              self ! Update( id + "@" + zipped._2, zipped._1, false)) // This time expandLine is false, avoid unlimited recursion
          }

        case Right(HttpResponse(NotFound, _, _, _)) => // couch returned not found, create it
          data.update(Put("/db/" + urlencode(id), Doc(id, None, value)))
          if (expandLines) { // expandLines defined, we will save each line to couch
            val lines = value.split("\n")
            lines.zip(lines.indices).foreach( zipped =>
              self ! Update( id + "@" + zipped._2, zipped._1, false)) // This time expandLine is false, avoid unlimited recursion
          }

        case ex => // Some request error
          println("Data was not saved: " + ex)
      }
    }
  }
}