# Microsoft Store listing texts

Copy each block into Partner Center → the product → Submission → Store listings → the language.
Limits: short description ≤ 1000 characters, description ≤ 10 000, each product feature ≤ 200,
up to 7 search terms (≤ 30 characters each, at most 21 words in total), screenshot caption ≤ 200.

Same in every language:

- Copyright and trademark info: `© 2026 WinGit Project. Licensed under Apache-2.0.`
- Website: `https://wgit.cz/`
- Support contact info: `https://github.com/biblelamp/WinGit/issues`
- Privacy policy URL (Properties): `https://wgit.cz/privacy/`

---

## English (en-us)

### Short description

Free, open-source desktop Git client: history graph, side-by-side diffs, staging, branches and merges in one window.

### Description

WinGit is a free, open-source desktop Git client. It shows everything you need for everyday work in one window: your repositories, changed files, the commit history graph and side-by-side diffs.

Commit, push, pull, branch and merge without the command line. Keep all your repositories in one list and see at a glance which ones have changes. WinGit fetches your remotes in the background, so new commits from your team show up on their own.

WinGit works with the Git you have installed and with your existing repositories, settings and credentials (Git Credential Manager, OpenSSH). It collects no data, needs no account and shows no ads.

The interface is available in English, Russian and Czech, with a light or dark theme.

Requirements: Git for Windows must be installed (https://git-scm.com/download/win).

WinGit is an early but working version, developed in the open at https://github.com/biblelamp/WinGit. Bug reports, ideas and pull requests are welcome.

### What's new in this version

Version 0.4.2:
- Switching to another branch when your uncommitted changes are in the way: WinGit offers to stash them, switch and bring them back
- After such a switch you see every Git command WinGit ran and its output
- The new branch window shows the commit the branch starts from
- Pasted text in a branch or tag name gets "_" instead of spaces, so a task title can be pasted as it is
- SSH key: choose the private key for SSH remotes in Settings, or a separate key for one repository
- New Repository Settings window: the Git user for commits (now editable), the address of origin and the SSH key
- When you edit the address you see how WinGit will sign in: with the SSH key or with a user name and token
- Clear explanations of SSH errors (changed or unknown server key, missing or rejected key) with what to do

Version 0.4.1:
- New merge window: before merging you see which commits come in and how many files change
- A small picture shows how the history will look after the merge
- WinGit checks in advance whether the merge will cause conflicts, and in which files
- Choose whether to move the branch forward when possible or always make a merge commit
- A warning when you have uncommitted changes before a merge
- Branch list: a local branch shows the remote it tracks (for example "→ origin") and the commits to push or pull; the full name is in the tooltip

Version 0.4.0:
- New history window: the whole history of the current branch with the graph. Open it with the History button or by double-clicking a commit
- Select a commit to see its author, date, parents and full message, the changed files and the diff of each file
- Filter commits by message, author, commit id, branch or tag
- Even a very long history opens quickly: the newest commits show up at once, older ones are read in the background
- Double-click a branch in the history window to switch to it
- The history window remembers its size, position and pane sizes
- New icon for the History button

Version 0.3.7:
- Amend the last commit (when it is not pushed yet)
- An unfinished commit message is kept as a draft
- The commit dialog shows the Git user (name and e-mail) of the new commit
- Diff: search (Ctrl+F), changed words highlighted, a strip with all changes of the file
- Double-click a file to open its diff in a separate window
- Settings: interval of the automatic fetch, or turn it off
- Fixed: in Russian and Czech, removing a repository from the list did not open the confirmation dialog

Version 0.3.6:
- Interface in English, Russian or Czech
- Light or dark theme
- New Settings window: language, theme and the global Git user (name and e-mail)
- Diff: jump to the previous or next change (Shift+F7 / F7) with a change counter
- First release in the Microsoft Store

Version 0.3.5:
- Stash: put your changes aside (optionally with new files), apply, apply and delete, or delete a stash
- Small fixes

Version 0.3.4:
- Help menu with the About window (versions of WinGit, Java and Git, with a button to copy them), the website and issue reporting
- The commit date you set is remembered for the repository
- Builds for Linux (DEB, RPM and a portable archive)

Version 0.3.3:
- Fetch: get new commits from the server without changing your files
- The repository list and the history show commits on the server that are not pulled yet

Version 0.3.2:
- Remotes are listed directly in the branches tree, with their address
- Security fix: updated JGit (CVE-2025-4949)

Version 0.3.1:
- Rename tags, also on the server
- Renamed files are shown as renamed, with the old name in the tooltip
- The window title shows the repository name and folder and the WinGit version

Version 0.3.0:
- First public version: repositories (open, create, clone), files, branches, history graph and side-by-side diff
- Stage, unstage and discard; commit, push and pull
- Create, rename, delete, switch and merge branches; tags

### Product features

- Commit history graph with branches, merges and tags
- Side-by-side diff with syntax highlighting
- Stage, unstage or discard files before you commit
- Commit, push, pull and fetch without the command line
- Create, rename, delete and merge branches
- Clone repositories, manage tags and stashes
- All your repositories in one list, the changed ones stand out
- Background fetch shows new commits from your remotes
- English, Russian and Czech; light and dark theme
- Free and open source (Apache-2.0), no account, no telemetry

### Search terms

```
git
git client
git gui
version control
source control
repository
diff
```

### Screenshot captions (in screenshot order)

1. Repositories, files, the commit history graph and the diff in one window
2. Write the commit message and choose the files to commit
3. Dark theme

---

## Русский (ru-ru)

### Краткое описание

Бесплатный Git-клиент с открытым исходным кодом: граф истории, сравнение версий бок о бок, индекс, ветки и слияния в одном окне.

### Описание

WinGit — бесплатный настольный Git-клиент с открытым исходным кодом. Всё, что нужно для повседневной работы, собрано в одном окне: ваши репозитории, изменённые файлы, граф истории коммитов и сравнение версий бок о бок.

Делайте commit, push и pull, создавайте ветки и выполняйте слияния без командной строки. Все репозитории хранятся в одном списке, а те, в которых есть изменения, сразу видны. WinGit в фоне получает изменения из удалённых репозиториев, поэтому новые коммиты коллег появляются сами.

WinGit работает с установленным у вас Git, с вашими репозиториями, настройками и учётными данными (Git Credential Manager, OpenSSH). Программа не собирает данные, не требует учётной записи и не показывает рекламу.

Интерфейс доступен на русском, английском и чешском языках, со светлой или тёмной темой.

Требования: должен быть установлен Git для Windows (https://git-scm.com/download/win).

WinGit — ранняя, но уже рабочая версия; разработка ведётся открыто на https://github.com/biblelamp/WinGit. Мы будем рады сообщениям об ошибках, идеям и pull request.

### Что нового в этой версии

Версия 0.4.2:
- Переход на другую ветку, когда мешают незакоммиченные изменения: WinGit предложит отложить их, переключиться и вернуть изменения обратно
- После такого перехода видны все команды Git, которые выполнил WinGit, и их вывод
- Окно новой ветки показывает коммит, от которого она создаётся
- Во вставленном имени ветки или тега пробелы заменяются на «_», так что название задачи можно вставить как есть
- SSH-ключ: выбор приватного ключа для SSH-адресов в настройках или отдельного ключа для одного репозитория
- Новое окно «Настройки репозитория»: пользователь Git для коммитов (теперь его можно изменить), адрес origin и SSH-ключ
- При вводе адреса видно, как WinGit будет входить: по SSH-ключу или по логину и токену
- Понятные объяснения SSH-ошибок (сменился или неизвестен ключ сервера, ключ не найден или отклонён) с подсказкой, что делать

Версия 0.4.1:
- Новое окно слияния: до слияния видно, какие коммиты придут и сколько файлов изменится
- Небольшая схема показывает, как будет выглядеть история после слияния
- WinGit заранее проверяет, будут ли конфликты, и в каких файлах
- Можно выбрать: сдвигать ветку вперёд, когда это возможно, или всегда создавать коммит слияния
- Предупреждение о незакоммиченных изменениях перед слиянием
- Список веток: у локальной ветки виден отслеживаемый удалённый репозиторий (например, «→ origin») и коммиты к отправке или получению; полное имя — в подсказке

Версия 0.4.0:
- Новое окно истории: вся история текущей ветки с графом. Открывается кнопкой «История» или двойным щелчком по коммиту
- Для выбранного коммита видны автор, дата, родители и полное сообщение, изменённые файлы и сравнение каждого файла
- Фильтр коммитов по сообщению, автору, id коммита, ветке или тегу
- Даже очень длинная история открывается быстро: новые коммиты видны сразу, старые дочитываются в фоне
- Двойной щелчок по ветке в окне истории переключает на неё
- Окно истории запоминает свой размер, положение и размеры панелей
- Новая иконка кнопки «История»

Версия 0.3.7:
- Исправление последнего коммита (amend), пока он не отправлен на сервер
- Недописанное сообщение коммита сохраняется как черновик
- В окне коммита видно, от чьего имени (имя и e-mail Git) он будет создан
- Сравнение версий: поиск (Ctrl+F), подсветка изменённых слов, полоса со всеми изменениями файла
- Двойной щелчок по файлу открывает сравнение в отдельном окне
- Настройки: интервал автоматического fetch или его отключение
- Исправлено: на русском и чешском языках не открывался диалог удаления репозитория из списка

Версия 0.3.6:
- Интерфейс на английском, русском или чешском
- Светлая или тёмная тема
- Новое окно «Настройки»: язык, тема и общий пользователь Git (имя и e-mail)
- Сравнение: переход к предыдущему или следующему изменению (Shift+F7 / F7) со счётчиком изменений
- Первый выпуск в Microsoft Store

Версия 0.3.5:
- Stash: отложить изменения (при желании вместе с новыми файлами), применить, применить и удалить или удалить stash
- Небольшие исправления

Версия 0.3.4:
- Меню «Справка» с окном «О программе» (версии WinGit, Java и Git и кнопка, чтобы их скопировать), сайтом и сообщением об ошибке
- Заданная дата коммита запоминается для репозитория
- Сборки для Linux (DEB, RPM и переносной архив)

Версия 0.3.3:
- Fetch: получить новые коммиты с сервера, не меняя ваши файлы
- В списке репозиториев и в истории видны коммиты на сервере, которые ещё не забраны через Pull

Версия 0.3.2:
- Удалённые репозитории показаны прямо в дереве веток, вместе с адресом
- Исправление безопасности: обновлён JGit (CVE-2025-4949)

Версия 0.3.1:
- Переименование тегов, в том числе на сервере
- Переименованные файлы показываются как переименованные, старое имя — в подсказке
- В заголовке окна видны имя и папка репозитория и версия WinGit

Версия 0.3.0:
- Первая публичная версия: репозитории (открыть, создать, клонировать), файлы, ветки, граф истории и сравнение в две колонки
- Индекс (добавить, убрать, отменить изменения); commit, push и pull
- Создание, переименование, удаление, переключение и слияние веток; теги

### Функции продукта

- Граф истории коммитов с ветками, слияниями и тегами
- Сравнение версий бок о бок с подсветкой синтаксиса
- Добавление файлов в индекс, удаление из индекса и отмена изменений перед коммитом
- Commit, push, pull и fetch без командной строки
- Создание, переименование, удаление и слияние веток
- Клонирование репозиториев, работа с тегами и stash
- Все репозитории в одном списке, изменённые сразу видны
- Фоновый fetch показывает новые коммиты из удалённых репозиториев
- Русский, английский и чешский языки; светлая и тёмная тема
- Бесплатно и с открытым кодом (Apache-2.0), без учётной записи и телеметрии

### Условия поиска

```
git
git клиент
git gui
контроль версий
система контроля версий
репозиторий
diff
```

### Подписи к снимкам экрана

1. Репозитории, файлы, граф истории коммитов и сравнение версий в одном окне
2. Создание коммита и выбор файлов для коммита
3. Тёмная тема

---

## Čeština (cs-cz)

### Krátký popis

Bezplatný Git klient s otevřeným zdrojovým kódem: graf historie, porovnání verzí vedle sebe, staging, větve a slučování v jednom okně.

### Popis

WinGit je bezplatný desktopový Git klient s otevřeným zdrojovým kódem. Vše, co potřebujete pro každodenní práci, najdete v jednom okně: své repozitáře, změněné soubory, graf historie commitů a porovnání verzí vedle sebe.

Provádějte commit, push a pull, vytvářejte větve a slučujte je bez příkazového řádku. Všechny repozitáře máte v jednom seznamu a ty se změnami jsou hned vidět. WinGit na pozadí stahuje změny ze vzdálených repozitářů, takže nové commity kolegů se objeví samy.

WinGit pracuje s vaším nainstalovaným Gitem, s vašimi repozitáři, nastavením a přihlašovacími údaji (Git Credential Manager, OpenSSH). Neshromažďuje žádná data, nevyžaduje účet a nezobrazuje reklamy.

Rozhraní je k dispozici v češtině, angličtině a ruštině, se světlým nebo tmavým motivem.

Požadavky: musí být nainstalován Git pro Windows (https://git-scm.com/download/win).

WinGit je raná, ale funkční verze, vyvíjená otevřeně na https://github.com/biblelamp/WinGit. Hlášení chyb, nápady i pull requesty jsou vítány.

### Co je nového v této verzi

Verze 0.4.2:
- Přepnutí na jinou větev, když překážejí necommitnuté změny: WinGit nabídne je odložit, přepnout a změny vrátit zpět
- Po takovém přepnutí uvidíte všechny příkazy Gitu, které WinGit spustil, i jejich výstup
- Okno nové větve ukáže commit, ze kterého větev vzniká
- Ve vloženém názvu větve nebo tagu se mezery nahradí „_“, takže název úkolu lze vložit tak, jak je
- SSH klíč: výběr soukromého klíče pro SSH adresy v Nastavení nebo samostatného klíče pro jeden repozitář
- Nové okno Nastavení repozitáře: uživatel Gitu pro commity (nyní lze upravit), adresa origin a SSH klíč
- Při zadávání adresy uvidíte, jak se WinGit přihlásí: SSH klíčem, nebo jménem a tokenem
- Srozumitelná vysvětlení chyb SSH (změněný nebo neznámý klíč serveru, chybějící nebo odmítnutý klíč) s radou, co dělat

Verze 0.4.1:
- Nové okno pro merge: ještě před merge vidíte, které commity přibudou a kolik souborů se změní
- Malé schéma ukáže, jak bude historie po merge vypadat
- WinGit předem zjistí, zda merge způsobí konflikty a ve kterých souborech
- Můžete zvolit, zda větev jen posunout dopředu, když to jde, nebo vždy vytvořit merge commit
- Upozornění na necommitnuté změny před merge
- Seznam větví: u lokální větve je vidět sledovaný vzdálený repozitář (například „→ origin“) a commity k odeslání nebo stažení; celé jméno je v nápovědě

Verze 0.4.0:
- Nové okno historie: celá historie aktuální větve s grafem. Otevře se tlačítkem Historie nebo dvojklikem na commit
- U vybraného commitu je vidět autor, datum, rodiče a celá zpráva, změněné soubory a porovnání každého souboru
- Filtr commitů podle zprávy, autora, id commitu, větve nebo tagu
- I velmi dlouhá historie se otevře rychle: nejnovější commity jsou vidět hned, starší se dočítají na pozadí
- Dvojklik na větev v okně historie na ni přepne
- Okno historie si pamatuje svou velikost, polohu a velikosti panelů
- Nová ikona tlačítka Historie

Verze 0.3.7:
- Úprava posledního commitu (amend), dokud není odeslán na server
- Nedokončená zpráva commitu se uloží jako koncept
- Okno commitu ukazuje, pod jakým uživatelem Gitu (jméno a e-mail) commit vznikne
- Porovnání verzí: hledání (Ctrl+F), zvýraznění změněných slov, pruh se všemi změnami souboru
- Dvojklik na soubor otevře porovnání v samostatném okně
- Nastavení: interval automatického fetch nebo jeho vypnutí
- Opraveno: v ruštině a češtině se neotevřel dialog pro odebrání repozitáře ze seznamu

Verze 0.3.6:
- Rozhraní v angličtině, ruštině nebo češtině
- Světlý nebo tmavý motiv
- Nové okno Nastavení: jazyk, motiv a globální uživatel Gitu (jméno a e-mail)
- Porovnání: přechod na předchozí nebo další změnu (Shift+F7 / F7) s počítadlem změn
- První vydání v Microsoft Store

Verze 0.3.5:
- Stash: odložit změny (volitelně i s novými soubory), použít, použít a smazat nebo smazat stash
- Drobné opravy

Verze 0.3.4:
- Nabídka Nápověda s oknem O aplikaci (verze WinGit, Javy a Gitu s tlačítkem pro jejich zkopírování), webem a nahlášením chyby
- Zadané datum commitu se pro repozitář pamatuje
- Sestavení pro Linux (DEB, RPM a přenosný archiv)

Verze 0.3.3:
- Fetch: načtení nových commitů ze serveru beze změny vašich souborů
- Seznam repozitářů a historie ukazují commity na serveru, které ještě nejsou stažené přes Pull

Verze 0.3.2:
- Vzdálené repozitáře jsou přímo ve stromu větví, i s adresou
- Bezpečnostní oprava: aktualizovaný JGit (CVE-2025-4949)

Verze 0.3.1:
- Přejmenování tagů, i na serveru
- Přejmenované soubory se zobrazují jako přejmenované, původní název je v nápovědě
- Titulek okna ukazuje název a složku repozitáře a verzi WinGit

Verze 0.3.0:
- První veřejná verze: repozitáře (otevřít, vytvořit, klonovat), soubory, větve, graf historie a porovnání ve dvou sloupcích
- Stage, unstage a zahození změn; commit, push a pull
- Vytváření, přejmenování, mazání, přepínání a merge větví; tagy

### Funkce produktu

- Graf historie commitů s větvemi, slučováním a tagy
- Porovnání verzí vedle sebe se zvýrazněním syntaxe
- Přidání souborů do stage, odebrání ze stage nebo zahození změn před commitem
- Commit, push, pull a fetch bez příkazového řádku
- Vytváření, přejmenování, mazání a slučování větví
- Klonování repozitářů, správa tagů a stashů
- Všechny repozitáře v jednom seznamu, změněné jsou hned vidět
- Fetch na pozadí ukazuje nové commity ze vzdálených repozitářů
- Čeština, angličtina a ruština; světlý a tmavý motiv
- Zdarma a open source (Apache-2.0), bez účtu a bez telemetrie

### Hledané výrazy

```
git
git klient
git gui
správa verzí
verzovací systém
repozitář
diff
```

### Popisky snímků obrazovky

1. Repozitáře, soubory, graf historie commitů a porovnání verzí v jednom okně
2. Zpráva commitu a výběr souborů do commitu
3. Tmavý motiv
