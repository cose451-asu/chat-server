lazy val common = Seq(
  organization := "com.github.doohochang",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.12.8",
  scalacOptions ++= Seq(
    "-Ypartial-unification",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-deprecation"
  ),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    Resolver.bintrayRepo("dnvriend", "maven")
  ),
  Compile / PB.targets := Seq(
    scalapb.gen(flatPackage = true) -> (Compile / sourceManaged).value
  )
)

lazy val root =
  (project in file("."))
    .settings(common)
    .settings(
      name := "chat-server",
      libraryDependencies ++= Dependencies.totalModules,
      Compile / PB.protoSources := Seq(file("src/main/protobuf/serialization"), file("src/main/protobuf/service")),
      Compile / mainClass := Some("com.doohochang.chat.App")
    )

lazy val grpcTest =
  (project in file("chat-grpc-test"))
    .dependsOn(root)
    .settings(common)
    .settings(
      name := "chat-grpc-test",
      libraryDependencies ++= Dependencies.totalModules,
      Compile / mainClass := Some("com.doohochang.chat.GrpcTest")
    )
