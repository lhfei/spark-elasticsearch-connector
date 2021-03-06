enablePlugins(Antlr4Plugin)

val sparkVersion = "2.4.3"
val jodaVersion = "2.10.5"
val jacksonVersion = "2.10.3"
val apacheHttpVersion = "4.5.11"

val myDependencies = Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",
  "joda-time" % "joda-time" % jodaVersion,
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-smile" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % jacksonVersion,
  "com.fasterxml.jackson.jaxrs" % "jackson-jaxrs-smile-provider" % jacksonVersion,
  "org.apache.httpcomponents" % "httpclient" % apacheHttpVersion,
  "io.spray" %% "spray-json" % "1.3.5",
  "org.scalatest" %% "scalatest" % "3.1.1" % Test
)

lazy val commonSettings = Seq(
  organization := "org.rzlabs",
  version := "1.0.0-SNAPSHOT",
  scalaVersion := "2.11.12",
  antlr4Version in Antlr4 := "4.7.1",
  antlr4PackageName in Antlr4 := Some("org.apache.spark.sql.catalyst.elastic.parser"),
  antlr4GenListener in Antlr4 := true,
  antlr4GenVisitor in Antlr4 := true,
  antlr4TreatWarningsAsErrors in Antlr4 := true,
//  scalacOptions += "-target:jvm-1.8",
//  javacOptions in compile ++= Seq("-source", "1.8", "-target", "1.8"),
//  test in assembly := {}
)

lazy val root = (project in file("."))
  .settings(
    commonSettings,
    name := "spark-elasticsearch-connector",
    libraryDependencies ++= myDependencies
  )

assemblyMergeStrategy in assembly := {
  case PathList("module-info.class", xs @ _*) => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
