package team.startup.expo.support

import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoFeature
import team.startup.expo.global.client.expo.ExpoFeaturesResDto

/** Mockito `ExpoClient` 목이 이 서비스가 검사하는 기능을 모두 실효 ON으로 돌려주게 한다(이관 뒤 기존 박람회와 같다). */
fun ExpoClient.stubAllFeaturesOn() {
    val features = ExpoFeature.entries.map { mapOf("key" to it.name, "configuredEnabled" to true, "effectiveEnabled" to true) }
    doReturn(ExpoFeaturesResDto(features)).`when`(this).getFeatures(anyString())
}
