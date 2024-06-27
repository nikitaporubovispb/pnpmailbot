import sbt.*

object Dependencies {
  object Versions {
    val catsEffect = "3.5.4"
    val cats = "2.12.0"
    val pureconfig = "0.17.7"
    val logstage = "1.2.8"
    val canoe = "0.6.0"
    val mail = "2.0.1"
    val jsoup = "1.17.2"
    val skunk = "1.1.0-M3"
  }

  val distage: Seq[ModuleID] = Seq(
    "io.7mind.izumi" %% "logstage-core" % Versions.logstage,
  )

  val misc: Seq[ModuleID] = Seq(
    "org.augustjune"        %% "canoe"            % Versions.canoe,
    "org.typelevel"         %% "cats-effect"      % Versions.catsEffect,
    "org.typelevel"         %% "cats-core"        % Versions.cats,
    "com.github.pureconfig" %% "pureconfig-core"  % Versions.pureconfig,
    "com.sun.mail"          % "jakarta.mail"      % Versions.mail,
    "org.jsoup"             % "jsoup"             % Versions.jsoup,
    "org.tpolecat"          %% "skunk-core"       % Versions.skunk,
  )

  val all: Seq[ModuleID] = misc ++ distage
}
