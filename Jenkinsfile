pipeline {
  agent any
  stages {
  
    stage('Build') {
      steps {
        sh 'mvn clean install'
      }
    }
    
  }

  post {
  
    success {
      junit 'target/surefire-reports/**/*.xml'
      jacoco(execPattern: 'target/jacoco.exec') 
      cleanWs()
    }
    
  }
}