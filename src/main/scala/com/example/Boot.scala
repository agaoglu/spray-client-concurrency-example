package com.example

import akka.config.Supervision._
import akka.actor.Supervisor
import akka.actor.Actor._
import cc.spray._
import cc.spray.can.{HttpServer, HttpClient}
import org.slf4j.LoggerFactory

object Boot extends App {
  
  LoggerFactory.getLogger(getClass)
  
  val mainModule = new HelloService {
    // bake your module cake here
  }

  val httpService    = actorOf(new HttpService(mainModule.helloService))
  val rootService    = actorOf(new SprayCanRootService(httpService))
  val sprayCanServer = actorOf(new HttpServer())
  
  Supervisor(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 100),
      List(
        Supervise(httpService, Permanent),
        Supervise(rootService, Permanent),
        Supervise(sprayCanServer, Permanent),
        Supervise(actorOf(new HttpClient), Permanent),
        Supervise(actorOf(new Web), Permanent),
        Supervise(actorOf(new Db), Permanent)
      )
    )
  )
}