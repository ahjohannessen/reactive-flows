import sbt._

object Version {
  val akka                 = "2.3.6"
  val akkaDataReplication  = "0.7"
  val akkaHttp             = "0.10.0-M2"
  val akkaPersistenceMongo = "0.7.4"
  val logback              = "1.1.2"
  val scala                = "2.11.4"
  val scalaTest            = "2.2.2"
  val sprayJson            = "1.2.6"
}

object Library {
  val akkaActor            = "com.typesafe.akka"   %% "akka-actor"                    % Version.akka
  val akkaContrib          = "com.typesafe.akka"   %% "akka-contrib"                  % Version.akka
  val akkaDataReplication  = "com.github.patriknw" %% "akka-data-replication"         % Version.akkaDataReplication
  val akkaHttp             = "com.typesafe.akka"   %% "akka-http-experimental"        % Version.akkaHttp
  val akkaPersistenceMongo = "com.github.ddevore"  %% "akka-persistence-mongo-casbah" % Version.akkaPersistenceMongo
  val akkaSlf4j            = "com.typesafe.akka"   %% "akka-slf4j"                    % Version.akka
  val akkaTestkit          = "com.typesafe.akka"   %% "akka-testkit"                  % Version.akka
  val logbackClassic       = "ch.qos.logback"      %  "logback-classic"               % Version.logback
  val scalaTest            = "org.scalatest"       %% "scalatest"                     % Version.scalaTest
  val sprayJson            = "io.spray"            %% "spray-json"                    % Version.sprayJson
}

object Resolver {
  val patriknw = "patriknw at bintray" at "http://dl.bintray.com/patriknw/maven"
}
