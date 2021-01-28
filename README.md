# Jenkins Gating

[![Build Status](https://ci.jenkins.io/job/Plugins/job/gating-core-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/gating-core-plugin/job/master/)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/gating-core-plugin.svg)](https://github.com/jenkinsci/gating-core-plugin/graphs/contributors)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/gating-core.svg)](https://plugins.jenkins.io/gating-core)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/gating-core-plugin.svg?label=changelog)](https://github.com/jenkinsci/gating-core-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/gating-core.svg?color=blue)](https://plugins.jenkins.io/gating-core)

## Introduction

Pause builds when required infrastructure is not ready for the build to run.

The plugin provides reusable components for Jenkins builds to either wait in queue or pause a
Jenkins pipeline when the infrastructure is unavailable. Backend plugins are responsible for submitting
the update data, so they can be consumed through this plugin.

Note that this plugin alone does not monitor any resources, nor it pulls resource status from anywhere. It relies on those source
plugins to do so.

## Getting started

Install this plugin and necessary backend plugins to read the uptime information from the sources you use to track
infrastructure uptime. Once such plugin(s) are configured and provides data into Jenkins, builds can utilize the data.

See what matrices are available to Jenkins Gating at JENKINS_URL/gating/. 

### Holding builds in queue

Add "Gating requirement" property for your jobs and select which of the resources needs to be up in order for the build
to be scheduled. Then everytime the build gets scheduled, it will only leave the queue when all the resources are up.

### Configuring via Job DSL

```groovy
job('my-job') {
  properties {
    requireResources {
      resources(['my-service', 'my-other-service'])
    }
    // ...
  }
  // ...
}
```

### Pausing pipeline execution

Alternatively, part of the build can gate certain resources using a pipeline block-step:

```groovy
// ... pipeline code ...
requireResources(resources: ['my-service', 'my-other-service']) {
  echo 'Executed only when both the services are up'
}
// ... more pipeline code ...
``` 

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)
