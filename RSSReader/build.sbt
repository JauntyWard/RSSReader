name := "RSSReader"

version := "1.0"

lazy val `rssreader` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq( jdbc , anorm , cache , ws )

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )

libraryDependencies += "wabisabi" %% "wabisabi" % "2.0.14"

resolvers += "gphat" at "https://raw.github.com/gphat/mvn-repo/master/releases/"

libraryDependencies += "com.sksamuel.elastic4s" %% "elastic4s" % "1.4.12"

libraryDependencies += "org.jsoup" % "jsoup" % "1.7.2"

