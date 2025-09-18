#!/usr/bin/env groovy

def gitShortCommit=''
def date=''
def app_name='milvus-java-sdk-tools'
def github='https://github.com/yongpengli-z/milvus-java-sdk-tools.git'

pipeline {
   options{
    disableConcurrentBuilds(abortPrevious: true)
    skipDefaultCheckout()
   }
   agent {
        kubernetes {
            inheritFrom 'default'
            defaultContainer 'main'
            yaml """
apiVersion: v1
kind: Pod
metadata:
  namespace: jenkins
spec:
  nodeSelector:
    node-role/jenkins-job: 'true'
    kubernetes.io/arch: 'amd64'
  containers:
  - name: main
    image: harbor-us-vdc.zilliz.cc/devops/cd-base:latest
    args: ["cat"]
    tty: true
  - name: maven
    image: maven:3.8.4-jdk-8-slim
    args: ["cat"]
    tty: true
    volumeMounts:
    - mountPath: "/apps/.m2/"
      name: "maven-settings"
  - name: kaniko
    imagePullPolicy: IfNotPresent
    image: harbor-us-vdc.zilliz.cc/devops/gcr-kaniko-project-executor:debug
    command:
    - /busybox/cat
    tty: true
    volumeMounts:
      - name: kaniko-secret
        mountPath: /kaniko/.docker/
  volumes:
    - configMap:
        name: "maven-settings"
      name: "maven-settings"
    - name: kaniko-secret
      secret:
        secretName: kaniko-secret-us
        items:
          - key: .dockerconfigjson
            path: config.json
"""
            customWorkspace '/home/jenkins/agent/workspace'
        }
   }
    parameters {
        gitParameter branchFilter: 'origin/(.*)', defaultValue: 'master', name: 'BRANCH', type: 'PT_BRANCH'
    }
   environment{
      DOCKER_IMAGE="harbor-us-vdc.zilliz.cc/qa/${app_name}"
      GITHUB_TOKEN_ID="github-token"
      GIT_REPO="${github}"
      GIT_COMMIT=""
   }
    stages {
        stage('Checkout'){
            steps{
                git  credentialsId: 'zilliz-ci',  branch: "${params.BRANCH}", url: "${github}"
                script {
                    date = sh(returnStdout: true, script: 'date +%Y%m%d').trim()

                    gitShortCommit = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    GIT_COMMIT=sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
                    image_tag="${params.BRANCH}-${date}-${gitShortCommit}"
                }
            }
        }
        stage('Build ') {
            steps{
                container(name: 'maven') {
                  script {
                    sh 'ls -lah '
                    sh """
                        mvn clean package -DskipTests
                    """                
                  }
               }
            }
        }

        stage('Build & Publish Image') {
            steps{
                container(name: 'kaniko',shell: '/busybox/sh') {
                  script {
                    sh 'ls -lah '
                    sh """
                    executor \
                    --context="`pwd`" \
                    --registry-mirror="nexus-nexus-repository-manager-docker-5000.nexus:5000"\
                    --insecure-registry="nexus-nexus-repository-manager-docker-5000.nexus:5000" \
                    --build-arg=GIT_REPO=${GIT_REPO} \
                    --build-arg=GIT_BRANCH=${params.BRANCH} \
                    --build-arg=GIT_COMMIT_HASH=${GIT_COMMIT} \
                    --dockerfile "ci/docker/Dockerfile" \
                    --destination=${DOCKER_IMAGE}:${image_tag}
                    """
                  }
                }
            }
        }
    }
}
