package dapex

import cats.Parallel
import cats.data.NonEmptyList
import cats.effect._
import cats.effect.std.Dispatcher
import com.comcast.ip4s._
import dapex.config.{RabbitMQConfig, ServerConfiguration}
import dapex.guardrail.healthcheck.HealthcheckResource
import dapex.rabbitmq.Rabbit
import dapex.rabbitmq.publisher.DapexMQPublisher
import dapex.server.domain.healthcheck.{
  HealthCheckService,
  HealthChecker,
  HealthcheckAPIHandler,
  SelfHealthCheck
}
import dapex.server.domain.rmq.DapexMessgeHandlerConfigurator
import dapex.server.domain.server.entities.Service
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.AMQPChannel
import io.circe.config.parser
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.middleware.Logger
import org.typelevel.log4cats.{Logger => Log4CatsLogger}

object AppServer {

  def createServer[F[_]: Async: Log4CatsLogger: Parallel](): Resource[F, Service[F]] =
    for {
      conf: ServerConfiguration <- Resource.eval(
        parser.decodePathF[F, ServerConfiguration](path = "server")
      )
      rmqConf: RabbitMQConfig = conf.rabbitMQ
      rmqDispatcher <- Dispatcher.parallel
      rmqClient: RabbitClient[F] <- Resource.eval(Rabbit.getRabbitClient(rmqConf, rmqDispatcher))
      aMQPChannel: AMQPChannel <- rmqClient.createConnectionChannel

      // Health checkers
      checkers = NonEmptyList.of[HealthChecker[F]](SelfHealthCheck[F])
      healthCheckers = HealthCheckService(checkers)
      healthRoutes = new HealthcheckResource().routes(
        new HealthcheckAPIHandler[F](healthCheckers)
      )

      // Routes and HTTP App
      allRoutes = healthRoutes.orNotFound
      httpApp = Logger.httpApp(logHeaders = true, logBody = true)(allRoutes)

      // Build server
      httpPort = Port.fromInt(conf.http.port.value)
      httpHost = Ipv4Address.fromString(conf.http.host.value)
      server: Server <- EmberServerBuilder.default
        .withPort(httpPort.getOrElse(port"8000"))
        .withHost(httpHost.getOrElse(ipv4"0.0.0.0"))
        .withHttpApp(httpApp)
        .build

      queueHandlers = DapexMessgeHandlerConfigurator.getHandlers()
      rmqPublisher = new DapexMQPublisher(rmqClient)
    } yield Service[F](server, rmqClient, queueHandlers.toList, rmqPublisher, aMQPChannel)
}
