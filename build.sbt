import com.banno.license.Plugin.LicenseKeys._
import com.banno.license.Licenses._
import sbtrelease._
import ReleaseStateTransformations._

licenseSettings

license := apache2("Copyright 2015 the original author or authors.")

removeExistingHeaderBlock := true

releaseSettings

organization  := "org.github"

name := "simple-akka"

scalaVersion  := "2.11.4"

resolvers ++= Seq("snapshots"     at "http://oss.sonatype.org/content/repositories/snapshots",
  "staging"       at "http://oss.sonatype.org/content/repositories/staging",
  "releases"        at "http://oss.sonatype.org/content/repositories/releases",
  "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
)

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

seq(net.virtualvoid.sbt.graph.Plugin.graphSettings : _*)

libraryDependencies ++= {
  val akkaV = "2.3.8"
  Seq(
    "ch.qos.logback"        %  "logback-classic"  % "1.1.2",
    "com.typesafe.akka"    %% "akka-actor"        % akkaV,
    "com.typesafe.akka"    %% "akka-slf4j"        % akkaV,
    "org.scalatest"        %%  "scalatest"        % "2.2.3" % "test",
    "com.typesafe.akka"    %% "akka-testkit"      % akkaV % "test"
  )
}

Revolver.settings