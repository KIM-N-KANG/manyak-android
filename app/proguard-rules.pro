# 라이브러리 규칙은 각 AAR 의 consumer rules 에 맡기고, 여기에는 그것으로 부족한 곳만 둔다.

# 로그인·신고 실패 분석 이벤트가 오류 종류를 클래스 이름(::class.simpleName)으로 보낸다.
# 이름이 줄면 이미 쌓인 이벤트 값과 이어지지 않는다. simpleName 은 InnerClass 속성에서 읽고,
# R8 은 바깥 클래스 이름까지 남아 있어야 그 속성을 유지한다.
-keepattributes InnerClasses,EnclosingMethod
-keepnames class app.manyak.common.domain.error.DomainError
-keepnames class app.manyak.common.domain.error.DomainError$*

# 카카오 SDK 는 오류 원인 enum 의 필드와 그 어노테이션을 이름으로 읽는데(ClientError 생성자의 getField),
# AAR 에 규칙을 싣지 않는다. 없으면 카카오 토큰이 없는 상태에서 앱 시작 직후 크래시한다. 카카오 공식 가이드의 규칙이다.
-keep class com.kakao.sdk.**.model.* { <fields>; }
