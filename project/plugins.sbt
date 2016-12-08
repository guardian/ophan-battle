addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1") // https://github.com/sbt/sbt-buildinfo/issues/88

addSbtPlugin("com.vmunier" % "sbt-web-scalajs" % "1.0.3")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.13")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.10")


// For processing Thrift
addSbtPlugin("com.twitter" %% "scrooge-sbt-plugin" % "4.8.0") // https://dl.bintray.com/sbt/sbt-plugin-releases/com.twitter/scrooge-sbt-plugin/scala_2.10/sbt_0.13/
//the twitter repository
resolvers += "twitter-repo" at "https://maven.twttr.com"
