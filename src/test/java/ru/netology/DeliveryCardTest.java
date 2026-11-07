package ru.netology;

import com.codeborne.selenide.SelenideElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Keys;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selenide.*;

class DeliveryCardTest {

    @BeforeEach
    void setUp() {
        // каждый тест открывает страницу заново — уведомление от прошлого теста
        // исчезает вместе с перезагрузкой, отдельный tearDown не нужен
        open("http://localhost:9999");
    }

    // генерация даты: не ранее трёх дней с текущей даты
    private String generateDate(int days) {
        return LocalDate.now().plusDays(days)
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    // очистка поля как действия пользователя: выделить всё и стереть
    private void fillField(String selector, String value) {
        SelenideElement field = $(selector);
        field.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        field.sendKeys(Keys.BACK_SPACE);
        field.setValue(value);
    }

    private void fillForm(String city, String date, String name, String phone) {
        fillField("[data-test-id=city] input", city);
        fillField("[data-test-id=date] input", date);
        fillField("[data-test-id=name] input", name);
        fillField("[data-test-id=phone] input", phone);
        $("[data-test-id=agreement]").click();
    }

    // кнопка отправки: у неё нет data-test-id, ищем по тексту
    private void submit() {
        $$("button").findBy(exactText("Забронировать")).click();
    }

    // проверка состояния загрузки и успешного бронирования по тексту страницы:
    // у уведомления в этой сборке нет надёжных data-test-id/классов
    private void waitForSuccess(String expectedDate) {
        // состояние загрузки не должно длиться дольше 15 секунд:
        // уведомление обязано появиться в этот срок, иначе тест падает
        $("body")
                .shouldHave(text("Встреча успешно забронирована на " + expectedDate),
                        Duration.ofSeconds(15));
    }

    private void successShouldBeHidden() {
        $("body").shouldNotHave(text("Встреча успешно забронирована"));
    }

    // ==================== Задача №1 ====================

    @Test
    void shouldSubmitSuccessfully() {
        // +4 дня — запас от границы «не ранее трёх дней»
        String date = generateDate(4);

        fillForm("Москва", date, "Анна Иванова-Смирнова", "+79271234567");
        submit();

        waitForSuccess(date);
    }

    @Test
    void shouldSubmitWithThreeDaysLaterDate() {
        // минимально допустимая дата по требованиям
        // «ё» валидатор SUT не пропускает, поэтому имя без неё
        String date = generateDate(3);

        fillForm("Москва", date, "Петр Ильичев", "+79001112233");
        submit();

        waitForSuccess(date);
    }

    // ---------- негативные сценарии ----------

    @Test
    void shouldShowErrorForTooEarlyDate() {
        String date = generateDate(1);

        fillForm("Москва", date, "Анна Иванова", "+79271234567");
        submit();

        $("[data-test-id=date] .input__sub")
                .shouldBe(visible)
                .shouldHave(text("Заказ на выбранную дату невозможен"));
        successShouldBeHidden();
    }

    @Test
    void shouldShowErrorForEmptyCity() {
        fillForm("", generateDate(4), "Анна Иванова", "+79271234567");
        submit();

        $("[data-test-id=city] .input__sub")
                .shouldBe(visible)
                .shouldHave(text("Поле обязательно для заполнения"));
    }

    @Test
    void shouldShowErrorForCityWithoutAdminCenter() {
        // город, не являющийся административным центром субъекта РФ
        fillForm("Тарко-Сале", generateDate(4), "Анна Иванова", "+79271234567");
        submit();

        // проверяем сам факт валидации: сообщение появилось и форма не отправилась
        $("[data-test-id=city] .input__sub").shouldBe(visible);
        successShouldBeHidden();
    }

    @Test
    void shouldShowErrorForLatinName() {
        fillForm("Москва", generateDate(4), "Anna Ivanova", "+79271234567");
        submit();

        $("[data-test-id=name] .input__sub")
                .shouldBe(visible)
                .shouldHave(text("Допустимы только русские буквы, пробелы и дефисы"));
        successShouldBeHidden();
    }

    @Test
    void shouldShowErrorForDigitsInName() {
        fillForm("Москва", generateDate(4), "Анна 123", "+79271234567");
        submit();

        $("[data-test-id=name] .input__sub").shouldBe(visible);
        successShouldBeHidden();
    }

    @Test
    void shouldShowErrorForInvalidPhone() {
        fillForm("Москва", generateDate(4), "Анна Иванова", "89271234567");
        submit();

        $("[data-test-id=phone] .input__sub")
                .shouldBe(visible)
                .shouldHave(text("Телефон указан неверно. Должно быть 11 цифр"));
        successShouldBeHidden();
    }

    @Test
    void shouldShowErrorForShortPhone() {
        fillForm("Москва", generateDate(4), "Анна Иванова", "+7");
        submit();

        $("[data-test-id=phone] .input__sub").shouldBe(visible);
        successShouldBeHidden();
    }

    @Test
    void shouldBlockSubmitWithoutAgreement() {
        fillField("[data-test-id=city] input", "Москва");
        fillField("[data-test-id=date] input", generateDate(4));
        fillField("[data-test-id=name] input", "Анна Иванова");
        fillField("[data-test-id=phone] input", "+79271234567");
        // флажок согласия не выставляем

        submit();

        // главное требование сценария: заявка не создаётся, уведомления нет
        successShouldBeHidden();
    }

    @Test
    void shouldShowErrorIfDateCleared() {
        // заполняем всё корректно, потом стираем дату и жмём отправить
        fillForm("Москва", generateDate(4), "Анна Иванова", "+79271234567");
        fillField("[data-test-id=date] input", "");
        submit();

        $("[data-test-id=date] .input__sub").shouldBe(visible);
        successShouldBeHidden();
    }

    // ==================== Задача №2 (комплексные элементы) ====================

    @Test
    void shouldPickCityFromDropdownAndDateFromCalendar() {
        // вводим две буквы — появляется выпадающий список с городами;
        // exactText: совпадает только сам пункт списка («Москва»), а не контейнер
        // со всем списком — иначе клик приходился на «Смоленск» внутри «Мо»-фильтра
        fillField("[data-test-id=city] input", "Мо");
        $$("*").filterBy(exactText("Москва")).first().click();
        $("[data-test-id=city] input").shouldHave(exactValue("Москва"));

        // выбираем дату на неделю вперёд через календарь
        LocalDate target = LocalDate.now().plusDays(7);
        String targetDate = target.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String targetDayNumber = String.valueOf(target.getDayOfMonth());

        $("[data-test-id=date] input").click();

        // сдвиг на неделю вперёд: если месяц поменялся, переключаем календарь кнопкой «вперёд»
        if (!target.format(DateTimeFormatter.ofPattern("MM.yyyy"))
                .equals(LocalDate.now().format(DateTimeFormatter.ofPattern("MM.yyyy")))) {
            $$("button").findBy(text(">")).click();
        }

        // точное совпадение числа: чтобы не поймать строку даты или число соседнего месяца
        $$("*").filterBy(exactText(targetDayNumber)).first().click();

        fillField("[data-test-id=name] input", "Анна Иванова");
        fillField("[data-test-id=phone] input", "+79271234567");
        $("[data-test-id=agreement]").click();
        submit();

        waitForSuccess(targetDate);
    }
}
