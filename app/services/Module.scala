package services

import play.api.inject.{SimpleModule, _}

final class Module extends SimpleModule((environment, configuration) => {
  EmbeddedPostgresBuilder.start(configuration)
  Seq(bind[EmbeddedDB].toProvider(classOf[EmbeddedPostgresBuilder]).eagerly())
})
