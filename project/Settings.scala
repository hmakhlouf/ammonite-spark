
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

import sbt._
import sbt.Def.{update => _, _}
import sbt.Keys._

object Settings {

  implicit class ProjectOps(val project: Project) extends AnyVal {
    def underModules: Project = {
      val base = project.base.getParentFile / "modules" / project.base.getName
      project.in(base)
    }
  }

  lazy val shared = Seq(
    scalaVersion := Deps.Scala.scala212,
    crossScalaVersions := Seq(Deps.Scala.scala212),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-explaintypes",
      "-encoding", "utf-8",
      "-unchecked"
    )
  )

  lazy val testSettings = Seq(
    (Test / fork) := true, // Java serialization goes awry without that
    testFrameworks += new TestFramework("utest.runner.Framework"),
    (Test / javaOptions) ++= Seq("-Xmx3g", "-Dfoo=bzz")
  )

  def generatePropertyFile(path: String) =
    (Compile / resourceGenerators) += Def.task {
      import sys.process._

      val dir = (Compile / classDirectory).value
      val ver = version.value

      val f = path.split('/').foldLeft(dir)(_ / _)
      f.getParentFile.mkdirs()

      val p = new java.util.Properties

      p.setProperty("version", ver)
      p.setProperty("commit-hash", Seq("git", "rev-parse", "HEAD").!!.trim)

      val w = new java.io.FileOutputStream(f)
      p.store(w, "Almond properties")
      w.close()

      state.value.log.info(s"Wrote $f")

      Seq(f)
    }

  lazy val generateDependenciesFile =
    (Compile / resourceGenerators) += Def.task {

      val dir = (Compile / classDirectory).value / "ammonite" / "spark"

      val configReport = (Compile / update)
        .value
        .configurations
        .find { configReport0 =>
          configReport0.configuration.name == Compile.name
        }
        .getOrElse {
          sys.error(s"Configuration report for ${Compile.name} not found")
        }

      val content = configReport
        .modules
        .filter(!_.evicted)
        .map(_.module)
        .map { mod =>
          assert(mod.crossVersion == CrossVersion.disabled)
          (mod.organization, mod.name, mod.revision)
        }
        .sorted
        .map {
          case (org, name, ver) =>
            s"$org:$name:$ver"
        }
        .mkString("\n")

      val f = dir / "amm-test-dependencies.txt"
      dir.mkdirs()

      Files.write(f.toPath, content.getBytes(UTF_8))

      state.value.log.info(s"Wrote $f")

      Seq(dir)
    }

}
