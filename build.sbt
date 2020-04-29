
inThisBuild(List(
  organization := "io.get-coursier.util",
  homepage := Some(url("https://github.com/coursier/cache-migration")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(
      "alexarchambault",
      "Alexandre Archambault",
      "",
      url("https://github.com/alexarchambault")
    )
  )
))
sonatypeProfileName := "io.get-coursier"

enablePlugins(PackPlugin)
scalaVersion := "2.13.1"
libraryDependencies ++= Seq(
  "io.get-coursier" %% "coursier-cache" % "2.0.0-RC6-13",
  "com.github.alexarchambault" %% "case-app" % "2.0.0-M16"
)
