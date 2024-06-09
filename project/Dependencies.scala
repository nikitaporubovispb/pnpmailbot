import sbt.*

object Dependencies {
  object Versions {
    val catsEffect = "3.5.4"
    val cats = "2.10.0"
    val pureconfig = "0.17.6"
    val logstage = "1.2.5"
    val canoe = "0.6.0"
  }

  val distage: Seq[ModuleID] = Seq(
    "io.7mind.izumi" %% "logstage-core" % Versions.logstage,
  )

  val misc: Seq[ModuleID] = Seq(
    "org.augustjune"        %% "canoe"            % Versions.canoe,
    "org.typelevel"         %% "cats-effect"      % Versions.catsEffect,
    "org.typelevel"         %% "cats-core"        % Versions.cats,
    "com.github.pureconfig" %% "pureconfig-core"  % Versions.pureconfig
  )

  val all: Seq[ModuleID] = misc ++ distage
}
