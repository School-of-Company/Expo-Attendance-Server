package team.startup.expo.domain.qr.support

import org.springframework.http.HttpStatus
import team.startup.expo.domain.qr.repository.DeletedExpoRepository
import team.startup.expo.global.exception.ExpectedException

/**
 * 박람회 공유 락([DeletedExpoRepository.lockExpoShared])을 잡은 뒤 삭제 기록을 확인한다. 삭제된 박람회면 404다.
 * 락을 먼저 잡으므로 삭제가 먼저 커밋됐다면 반드시 보이고, 삭제된 박람회의 기록이 되살아나지 않는다. 트랜잭션 안에서 부른다.
 * 공유 락이라 같은 박람회의 다른 쓰기와는 서로 기다리지 않는다(#248).
 */
fun DeletedExpoRepository.lockAndRequireAlive(expoId: String) {
    lockExpoShared(expoId)
    if (existsById(expoId)) throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")
}
