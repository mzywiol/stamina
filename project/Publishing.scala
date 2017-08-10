import sbt._
import sbt.Keys._
import xerial.sbt.Sonatype._
import SonatypeKeys._

object Publishing {
  lazy val publishingSettings = sonatypeSettings ++ Seq(
    publishTo := {
      val nexus = "http://maven.ocado.com/nexus/content/repositories/"

      if (isSnapshot.value)
        Some("Warehouse Stations Snapshots" at nexus + "stations-snapshots")
      else
        Some("Warehouse Stations Releases"  at nexus + "stations-releases")
    },
    credentials ++= (
      for {
        username <- sys.env.get("SONATYPE_USERNAME")
        password <- sys.env.get("SONATYPE_PASSWORD")
      } yield Credentials("Sonatype Nexus Repository Manager", "maven.ocado.com", username, password)).toSeq,
    pomExtra := (
  <url>https://github.com/scalapenos/stamina#readme</url>
  <scm>
    <url>git@github.com:scalapenos/stamina.git</url>
    <connection>scm:git@github.com:scalapenos/stamina.git</connection>
  </scm>
  <developers>
    <developer>
      <id>agemooij</id>
      <name>Age Mooij</name>
      <url>http://github.com/agemooij</url>
    </developer>
    <developer>
      <id>raboof</id>
      <name>Arnout Engelen</name>
      <url>http://github.com/raboof</url>
    </developer>
    <developer>
      <id>kkafara</id>
      <name>Kamil Kafara</name>
      <url>http://github.com/kkafara</url>
    </developer>
  </developers>
))
}
