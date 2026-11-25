package app.manyak.my.invite.domain

/**
 * 초대 공유에 싣는 웹 주소. 구현은 `BuildConfig` 를 가진 `:app` 이 제공한다.
 *
 * 아직 스토어 링크가 없어 웹 홈으로 보낸다 — 웹이 앱과 같은 계정으로 이어지므로 초대받은 쪽이
 * 어디서 열어도 코드를 등록할 수 있다.
 */
interface InviteShareLinkProvider {
    fun shareUrl(): String
}
