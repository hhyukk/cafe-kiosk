// build.gradle.kts는 toolchain으로 JDK 21을 요구하는데, Gradle은 설치된 JDK만 탐지할 뿐
// 없는 버전을 스스로 받아오지는 못한다. JDK 21이 없는 머신에서는 빌드가
// "Toolchain download repositories have not been configured" 로 실패한다.
// 이 플러그인이 toolchain 다운로드 저장소(foojay)를 등록해 Gradle이 JDK 21을 자동으로 받게 한다.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "App"
