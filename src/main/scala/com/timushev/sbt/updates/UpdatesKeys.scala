package com.timushev.sbt.updates

import com.timushev.sbt.updates.versions.Version
import sbt._

import scala.collection.immutable.SortedSet

trait UpdatesKeys {
  lazy val dependencyUpdatesReportFile = settingKey[File]("Dependency updates report file")
  lazy val dependencyUpdatesXmlReportFile = settingKey[File]("Dependency updates xml report file")
  lazy val dependencyUpdatesHtmlReportFile = settingKey[File]("Dependency updates html report file")
  lazy val dependencyUpdatesExclusions = settingKey[ModuleFilter]("Dependencies that are excluded from update reporting")
  lazy val dependencyUpdatesFailBuild = settingKey[Boolean]("Fail a build if updates found")
  lazy val dependencyAllowPreRelease = settingKey[Boolean]("If true, also take pre-release versions into consideration")
  lazy val dependencyUpdatesData = taskKey[Map[ModuleID, SortedSet[Version]]]("")
  lazy val dependencyUpdates = taskKey[Unit]("Shows a list of project dependencies that can be updated.")
  lazy val dependencyUpdatesReport = taskKey[File]("Writes a list of project dependencies that can be updated to a file.")
  lazy val dependencyUpdatesReportXml = taskKey[File]("Writes a list of project dependencies that can be updated in xml format to a file.")
  lazy val dependencyUpdatesReportHtml = taskKey[File]("Writes a list of project dependencies that can be updated in html format to a file.")
}

object UpdatesKeys extends UpdatesKeys
