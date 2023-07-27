package dapex

import cats.effect._
import dapex.server.domain.PublishAndConsumeRMQ
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object MainApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    Resource
      .eval(Slf4jLogger.create[IO])
      .use { implicit logger: Logger[IO] =>
        AppServer
          .createServer[IO]()
          .use { server =>
            PublishAndConsumeRMQ.run(
              server.rmqClient,
              server.handlers,
              server.rmqPublisher,
              server.channel
            )
          }
          .as(ExitCode.Success)
      }
}
