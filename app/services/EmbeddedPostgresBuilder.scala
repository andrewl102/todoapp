package services

import javax.inject.{Inject, Provider, Singleton}

import play.api.{Configuration, Environment}
import play.api.inject.{ApplicationLifecycle, Injector}
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres
import ru.yandex.qatools.embed.postgresql.distribution.Version.Main

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait EmbeddedDB


/**
  * Simple wrapper for embedding a Postgres instance.
  */
@Singleton
class EmbeddedPostgresBuilder(
                     environment: Environment,
                     configuration: Configuration,
                     lifecycle: ApplicationLifecycle,
                     maybeInjector: Option[Injector]
                   ) extends Provider[EmbeddedDB] {

  @Inject
  def this(
            environment: play.api.Environment,
            configuration: play.api.Configuration,
            lifecycle: play.api.inject.ApplicationLifecycle,
            injector: play.api.inject.Injector = play.api.inject.NewInstanceInjector
          ) = {
    this(environment, configuration, lifecycle, Option(injector))
  }


  override def get(): EmbeddedDB = {
    lifecycle.addStopHook(() => Future {
      EmbeddedPostgresBuilder.postgres.foreach(_.stop())
    })
    new EmbeddedDB {}
  }
}

//We define this here to load it before the default DB module complains about the DB not existing
object EmbeddedPostgresBuilder {
  var postgres: Option[EmbeddedPostgres] = None

  def start(configuration: Configuration):Unit = {
    if (configuration.get[Boolean]("embedded")) {
      postgres = Some(new EmbeddedPostgres(Main.V10))
      postgres.get.start("localhost", configuration.get[Int]("dbPort"), "todo_app")
    }
  }
}
