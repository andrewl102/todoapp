name := "todo-app"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.12.6"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies += guice
libraryDependencies += jdbc
libraryDependencies += evolutions

libraryDependencies += "ru.yandex.qatools.embed" % "postgresql-embedded" % "2.9"
libraryDependencies += "org.postgresql" % "postgresql" % "42.2.2"
libraryDependencies += "org.playframework.anorm" %% "anorm" % "2.6.2"

libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test

javaOptions in Test += "-Dconfig.file=conf/application.test.conf"