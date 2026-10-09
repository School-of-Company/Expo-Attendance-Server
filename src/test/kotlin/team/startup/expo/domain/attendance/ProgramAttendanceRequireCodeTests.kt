package team.startup.expo.domain.attendance

import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.global.client.application.ApplicationClient
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.user.StandardParticipantNamesReqDto
import team.startup.expo.global.client.user.UserClient
import team.startup.expo.support.IntegrationTestSupport

@TestPropertySource(properties = ["program-attendance.require-code=true"])
class ProgramAttendanceRequireCodeTests : IntegrationTestSupport() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var userClient: UserClient

    @MockitoBean
    lateinit var expoClient: ExpoClient

    @MockitoBean
    lateinit var applicationClient: ApplicationClient

    @Test
    fun `설정을 켜면 code가 없는 일반 프로그램 스캔은 400이고 유저 서비스를 부르지 않는다`() {
        mockMvc
            .perform(
                patch("/attendance/standard/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"expoId": "expo-req", "participantId": 5}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("참가자 코드가 필요합니다."))

        verify(userClient, never()).getStandardParticipantNames(StandardParticipantNamesReqDto("expo-req", listOf(5)))
    }
}
