package com.aibook.service.scraper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class DoubanCandidatesTest {
    @Test
    void returnsDistinctCandidatesWithoutRequiringIdenticalTitles() throws Exception {
        var scraper = new DoubanScraper();
        var candidates = scraper.parseSuggestCandidates(new ObjectMapper().readTree("""
                [{"id":"123","title":"三体：黑暗森林","author_name":"刘慈欣","year":"2008"},
                 {"id":"456","title":"三体（纪念版）","author_name":"刘慈欣","year":"2024"},
                 {"id":"123","title":"重复项"}, {"id":"https://invalid/","title":"无效"},
                 {"id":"789","title":"三体影视","type":"movie"}]
                """));
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).title()).isEqualTo("三体：黑暗森林");
        assertThat(candidates.get(1).sourceUrl()).isEqualTo("https://book.douban.com/subject/456/");
    }

    @Test
    void candidateResultsAreBoundedEvenWhenSourceReturnsManyEditions() {
        var array = new ObjectMapper().createArrayNode();
        for (int index = 0; index < 20; index++) {
            array.addObject().put("id", String.valueOf(index + 1)).put("title", "三体·版本" + index);
        }
        assertThat(new DoubanScraper().parseSuggestCandidates(array)).hasSize(8);
    }

    @Test
    void understandsDoubanSearchRedirectsButRejectsOtherHostsAndDeduplicates() {
        var candidates = new DoubanScraper().parseSearchCandidates(Jsoup.parse("""
                <div class='result'><div class='title'>
                  <a href='https://www.douban.com/link2/?url=https%3A%2F%2Fbook.douban.com%2Fsubject%2F123%2F'>三体：黑暗森林</a>
                </div><span class='subject-cast'>刘慈欣 / 2008</span></div>
                <a href='https://book.douban.com/subject/123/'>重复项</a>
                <a href='https://evil.example/book.douban.com/subject/456/'>恶意站点</a>
                """));
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).id()).isEqualTo("123");
        assertThat(candidates.get(0).author()).contains("刘慈欣");
    }

    @Test
    void fuzzyOrAmbiguousTitleIsNeverAutomaticallyAppliedAndEditionsRemainDistinct() {
        var scraper = spy(new DoubanScraper());
        var first = new DoubanScraper.Candidate("123", "三体：黑暗森林", "刘慈欣", "2008", "", "");
        doReturn(List.of(first)).when(scraper).searchCandidates("三体");
        assertThat(scraper.searchByTitle("三体")).isNull();
        verify(scraper, never()).getBookDetail(anyString());
        assertThat(DoubanScraper.normalizedTitle("三体（第一部）"))
                .isNotEqualTo(DoubanScraper.normalizedTitle("三体（第二部）"));
        doReturn(List.of(first, new DoubanScraper.Candidate("456", first.title(), "刘慈欣", "2024", "", "")))
                .when(scraper).searchCandidates(first.title());
        assertThat(scraper.searchByTitle(first.title())).isNull();
        verify(scraper, never()).getBookDetail(anyString());
    }

    @Test
    void onlyOneExactCandidateCanBeUsedAutomaticallyAndDetailsMustStillMatch() {
        var scraper = spy(new DoubanScraper());
        doReturn(List.of(new DoubanScraper.Candidate("123", "三体", "刘慈欣", "2008", "", "")))
                .when(scraper).searchCandidates("三体");
        doReturn(MetadataScraper.BookMetadata.builder().title("三体：黑暗森林").build())
                .when(scraper).getBookDetail("123");
        assertThat(scraper.searchByTitle("三体")).isNull();
        doReturn(MetadataScraper.BookMetadata.builder().title("三体").build())
                .when(scraper).getBookDetail("123");
        assertThat(scraper.searchByTitle("三体").getTitle()).isEqualTo("三体");
        assertThatThrownBy(() -> new DoubanScraper().getBookDetail("https://evil.example/"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
