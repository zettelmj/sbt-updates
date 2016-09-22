package com.timushev.sbt.updates

import java.io.{FileWriter, StringReader, StringWriter}
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.{StreamResult, StreamSource}

import com.timushev.sbt.updates.versions.Version
import sbt._
import sbt.std.TaskStreams

import scala.collection.immutable.SortedSet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.xml.Elem

object Reporter {

  import com.timushev.sbt.updates.UpdatesFinder._

  def dependencyUpdatesData(project: ModuleID,
                            dependencies: Seq[ModuleID],
                            resolvers: Seq[Resolver],
                            credentials: Seq[Credentials],
                            scalaVersions: Seq[String],
                            excluded: ModuleFilter,
                            allowPreRelease: Boolean,
                            out: TaskStreams[_]): Map[ModuleID, SortedSet[Version]] = {
    val loaders = resolvers collect MetadataLoaderFactory.loader(out.log, credentials)
    val updatesFuture = Future.sequence(scalaVersions map { scalaVersion =>
      val crossVersion = CrossVersion(scalaVersion, CrossVersion.binaryScalaVersion(scalaVersion))
      val crossDependencies = dependencies map crossVersion
      Future.sequence(crossDependencies map findUpdates(loaders, allowPreRelease))
    }) map { crossUpdates =>
      crossUpdates.transpose map { updates =>
        updates reduce (_ intersect _)
      }
    }
    val updates = Await.result(updatesFuture, 1.hour)
    (dependencies zip updates)
      .toMap
      .transform(exclude(excluded))
      .filterNot(_._2.isEmpty)
  }

  def extractDependencyUpdate(dependencyUpdates: Map[ModuleID, SortedSet[Version]]) = {
    dependencyUpdates.map {
      case (m, vs) =>
        val c = Version(m.revision)
        Seq(
          Some(formatModule(m)),
          Some(m.revision),
          patchUpdate(c, vs).map(_.toString),
          minorUpdate(c, vs).map(_.toString),
          majorUpdate(c, vs).map(_.toString)
        )
    }.toSeq.sortBy(_.head)
  }

  def gatherDependencyUpdates(dependencyUpdates: Map[ModuleID, SortedSet[Version]]): Seq[String] = {
    if (dependencyUpdates.isEmpty) Seq.empty
    else {
      val table = extractDependencyUpdate(dependencyUpdates)
      val widths = table.transpose.map {
        c => c.foldLeft(0) {
          _ max _.map(_.length).getOrElse(0)
        }
      }
      val separator = Seq("", " : ", " -> ", " -> ", " -> ")
      for (row <- table) yield {
        (separator zip row zip widths) map {
          case (_, 0) => ""
          case ((s, Some(v)), w) => s + pad(v, w)
          case ((s, None), w) => " " * (s.length + w)
        } mkString ""
      }
    }
  }

  def dependencyUpdatesReportTxt(project: ModuleID, dependencyUpdates: Map[ModuleID, SortedSet[Version]]): String = {
    val updates = gatherDependencyUpdates(dependencyUpdates)
    if (updates.isEmpty) "No dependency updates found for %s" format project.name
    else {
      val info = StringBuilder.newBuilder
      info.append("Found %s dependency update%s for %s" format(updates.size, if (updates.size > 1) "s" else "", project.name))
      updates.foreach {
        u =>
          info.append("\n  ")
          info.append(u)
      }
      info.toString()
    }
  }

  def dependencyUpdatesReportXml(project: ModuleID, dependencyUpdates: Map[ModuleID, SortedSet[Version]]): Elem = {
    //import scala.xml._

    val updates = extractDependencyUpdate(dependencyUpdates)

    <libraries>{
      updates.map {
        row => row match {
          case Seq(module, version, patchUpdate, minorUpdate, majorUpdate) => {
            <lib>
              <name>{module.getOrElse()}</name>
              <version>{ version.getOrElse("") }</version>
              <patchUpdate>{ patchUpdate.getOrElse("") }</patchUpdate>
              <minorUpdate>{ minorUpdate.getOrElse("") }</minorUpdate>
              <majorUpdate>{ majorUpdate.getOrElse("") }</majorUpdate>
            </lib>
          }
        }
      }
    }
    </libraries>
  }

  def displayDependencyUpdates(project: ModuleID, dependencyUpdates: Map[ModuleID, SortedSet[Version]], failBuild: Boolean, out: TaskStreams[_]): Unit = {
    out.log.info(dependencyUpdatesReportTxt(project, dependencyUpdates))
    if (failBuild && dependencyUpdates.nonEmpty) sys.error("Dependency updates found")
  }

  def writeDependencyUpdatesReport(project: ModuleID, dependencyUpdates: Map[ModuleID, SortedSet[Version]], file: File, out: TaskStreams[_]): File = {
    IO.write(file, dependencyUpdatesReportTxt(project, dependencyUpdates) + "\n")
    out.log.info("Dependency update report written to %s" format file)
    file
  }

  def writeDependencyUpdatesReportHtml(project: ModuleID, dependencyUpdates: Map[ModuleID, SortedSet[Version]], file: File, out: TaskStreams[_]): File = {
    val report = dependencyUpdatesReportXml(project, dependencyUpdates)

    val str = new StringWriter
    scala.xml.XML.write(str, report, "UTF-8", true, null)

    val factory = TransformerFactory.newInstance();
    val transformer = factory.newTransformer(new StreamSource(getClass.getClassLoader.getResourceAsStream("report.xsl")))
    transformer.transform(new StreamSource(new StringReader(str.getBuffer.toString)), new StreamResult(file))

    out.log.info("Dependency update report written to %s" format file)
    file
  }

  def writeDependencyUpdatesReportXml(project: ModuleID, dependencyUpdates: Map[ModuleID, SortedSet[Version]], file: File, out: TaskStreams[_]): File = {
    val report = dependencyUpdatesReportXml(project, dependencyUpdates)

    val str = new StringWriter
    scala.xml.XML.write(str, report, "UTF-8", true, null)
    IO.write(file, str.getBuffer.toString)

    out.log.info("Dependency update report written to %s" format file)
    file
  }

  def formatModule(module: ModuleID) =
    module.organization + ":" + module.name + module.configurations.map(":" + _).getOrElse("")

  def patchUpdate(c: Version, updates: SortedSet[Version]) =
    updates.filter {
      v => v.major == c.major && v.minor == c.minor
    }.lastOption

  def minorUpdate(c: Version, updates: SortedSet[Version]) =
    updates.filter {
      v => v.major == c.major && v.minor > c.minor
    }.lastOption

  def majorUpdate(c: Version, updates: SortedSet[Version]) =
    updates.filter {
      v => v.major > c.major
    }.lastOption

  def pad(s: String, w: Int) = s.padTo(w, ' ')

  def exclude(excluded: ModuleFilter)(module: ModuleID, versions: SortedSet[Version]): SortedSet[Version] = {
    versions.filterNot { version => excluded.apply(module.copy(revision = version.toString)) }
  }

}
