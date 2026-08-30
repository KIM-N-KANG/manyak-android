package app.manyak.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.manyak.core.ui.R

/** 기본 서체. UI 전반에 쓴다. */
val Pretendard =
    FontFamily(
        Font(R.font.pretendard_regular, FontWeight.Normal),
        Font(R.font.pretendard_medium, FontWeight.Medium),
        Font(R.font.pretendard_bold, FontWeight.Bold),
    )

/**
 * 서사 서체. 스토리 본문([ManyakTypography.bodyReading]) 전용이며 UI 요소에 쓰지 않는다.
 *
 * **TTF 가 아니라 OTF 를 쓴다** — 배포된 TTF 에는 TrueType 힌팅(`fpgm`·`prep`·`cvt`)이 들어 있어
 * 작은 크기에서 획이 픽셀 격자에 맞춰지며 글자마다 굵기가 들쭉날쭉해진다.
 */
val MaruBuri =
    FontFamily(
        Font(R.font.maru_buri_regular, FontWeight.Normal),
        Font(R.font.maru_buri_bold, FontWeight.Bold),
    )

/**
 * Semantic — 타이포 롤. 이름은 토큰 정본의 `typography.*`와 1:1로 대응한다.
 *
 * 행간은 배수가 아니라 sp 절대값이다. 배수로 두면 플랫폼마다 반올림이 갈린다.
 *
 * 굵기는 Regular·Medium·Bold만 쓴다. SemiBold(600)를 쓰지 않는 이유는 번들에 없는 굵기를 요구하면
 * Bold로 대체 렌더되어 의도보다 두꺼워지기 때문이다.
 *
 * [titleMediumStrong]·[bodyLargeStrong]·[bodyMediumStrong] 만 크기가 아니라 굵기로 갈리는 롤이다. 이름을 `titleSmall`
 * 같은 크기 이름으로 두지 않은 것은, 크기 이름이 굵기 차이를 뜻하게 되면 스케일이 거짓말을 하기
 * 때문이다.
 */
@Immutable
data class ManyakTypography(
    val labelSmall: TextStyle,
    val labelLarge: TextStyle,
    val bodySmall: TextStyle,
    val bodyMedium: TextStyle,
    val bodyMediumStrong: TextStyle,
    val bodyLarge: TextStyle,
    val bodyLargeStrong: TextStyle,
    val bodyReading: TextStyle,
    val bodyReadingSmall: TextStyle,
    val titleMedium: TextStyle,
    val titleMediumStrong: TextStyle,
    val titleLarge: TextStyle,
    val headlineSmall: TextStyle,
)

/**
 * 읽기용 롤의 줄 상자. CSS `line-height` 와 같은 규칙으로 맞춘 값이다.
 *
 * `Trim.None` — 행간이 만든 여유를 첫 줄 위·마지막 줄 아래에서도 남긴다. Compose 기본값
 * `Trim.Both` 는 잘라내지만 CSS 는 남긴다.
 *
 * `Alignment.Center` — 그 여유를 위아래로 **똑같이** 나눈다. `Proportional` 은 ascent:descent
 * 비율로 나누는데, 마루부리는 그 비율이 4:1 이라 위만 크게 벌어진다.
 */
private val ReadingLineHeight =
    LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

internal val ManyakDefaultTypography =
    ManyakTypography(
        // 타임스탬프·최소 보조 문구
        labelSmall =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        // 버튼·탭 라벨
        labelLarge =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        // 메타 정보·보조 설명
        bodySmall =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        // 본문 기본
        bodyMedium =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        // 본문 옆에서 값 하나를 세우는 자리. bodyMedium 과 크기·행간이 같고 굵기로만 갈린다
        bodyMediumStrong =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        // 강조 본문·입력 필드
        bodyLarge =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        // 본문 안의 작은 제목. bodyLarge 와 크기·행간이 같고 굵기로만 갈린다
        bodyLargeStrong =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        // 스토리 본문. 장문 전용이라 행간을 1.75로 벌리고 자간을 2% 좁힌다
        bodyReading =
            TextStyle(
                fontFamily = MaruBuri,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.02).em,
                lineHeightStyle = ReadingLineHeight,
            ),
        // 짧은 서사 문장. 추천 입력처럼 읽는 글이지만 장문이 아닌 자리다.
        // 행간은 [bodyReading] 과 같은 1.75 배로 두어 두 롤이 같은 결로 읽힌다
        bodyReadingSmall =
            TextStyle(
                fontFamily = MaruBuri,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 24.5.sp,
                letterSpacing = (-0.02).em,
                lineHeightStyle = ReadingLineHeight,
            ),
        // 섹션·카드 제목
        titleMedium =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 26.sp,
            ),
        // 목록 섹션 제목. titleMedium 과 크기·행간이 같고 굵기로만 갈린다
        titleMediumStrong =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 26.sp,
            ),
        // 화면 제목
        titleLarge =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 27.sp,
            ),
        // 온보딩·랜딩 헤드라인
        headlineSmall =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
            ),
    )
