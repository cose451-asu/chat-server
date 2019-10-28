import sbt._

object Dependencies {
  lazy val akkaVersion = "2.5.23"
  lazy val akkaModules: Seq[ModuleID] = Seq(
    "akka-actor",
    "akka-stream",
    "akka-cluster",
    "akka-cluster-sharding",
    "akka-distributed-data",
    "akka-persistence",
    "akka-persistence-query",
    "akka-actor-typed",
    "akka-stream-typed",
    "akka-cluster-typed",
    "akka-cluster-sharding-typed",
    "akka-persistence-typed",
    "akka-actor-testkit-typed"
  ).map("com.typesafe.akka" %% _ % akkaVersion)

  lazy val akkaPersistenceJDBCModules: Seq[ModuleID] =
    Seq("com.github.dnvriend" %% "akka-persistence-jdbc" % "3.5.2")

  lazy val slickVersion = "3.3.1"
  lazy val slickModules: Seq[ModuleID] =
    Seq("slick", "slick-hikaricp")
      .map("com.typesafe.slick" %% _ % slickVersion)

  lazy val slickPgVersion = "0.18.0"
  lazy val slickPgModules: Seq[ModuleID] =
    Seq("slick-pg", "slick-pg_joda-time")
      .map("com.github.tminglei" %% _ % slickPgVersion)

  lazy val postgresqlModules: Seq[ModuleID] =
    Seq("org.postgresql" % "postgresql" % "42.2.5")

  lazy val loggingModules: Seq[ModuleID] = Seq(
//    "org.slf4j" % "slf4j-nop" % "1.7.26",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
    "com.typesafe.akka" %% "akka-slf4j" % "2.5.23"
  )

  lazy val akkaPersistenceInmemoryModules: Seq[ModuleID] =
    Seq("com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.2")

  lazy val scalaTestVersion = "3.0.8"
  lazy val scalaTestModules: Seq[ModuleID] = Seq(
    "org.scalactic" %% "scalactic" % scalaTestVersion,
    "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
  )

  lazy val catsModules: Seq[ModuleID] = Seq(
    "org.typelevel" %% "cats-core" % "2.0.0-M4",
    "org.typelevel" %% "cats-effect"% "2.0.0-M4",
  )

  lazy val shapelessModules: Seq[ModuleID] =
    Seq("com.chuusai" %% "shapeless" % "2.3.3")

  lazy val kittenModules: Seq[ModuleID] =
    Seq("org.typelevel" %% "kittens" % "2.0.0-M1")

  lazy val scalapbModules: Seq[ModuleID] =
    Seq("com.thesamet.scalapb" %% "compilerplugin" % "0.9.0-M7")

  lazy val grpcModules: Seq[ModuleID] = Seq(
    "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
  )

  lazy val nscalaTimeModules: Seq[ModuleID] =
    Seq("com.github.nscala-time" %% "nscala-time" % "2.22.0")

  lazy val circeVersion = "0.11.1"
  lazy val circeModules: Seq[ModuleID] =
    Seq("circe-core", "circe-generic", "circe-parser")
      .map("io.circe" %% _ % circeVersion)

  lazy val jwtScalaModules: Seq[ModuleID] =
    Seq("com.pauldijou" %% "jwt-circe" % "3.1.0")

  lazy val totalModules: Seq[ModuleID] =
    akkaModules ++
      akkaPersistenceJDBCModules ++ slickModules ++ slickPgModules ++ postgresqlModules ++ akkaPersistenceInmemoryModules ++
      catsModules ++ shapelessModules ++ kittenModules ++ scalaTestModules ++ loggingModules ++
      scalapbModules ++ grpcModules ++
      nscalaTimeModules ++
      circeModules ++ jwtScalaModules
}
