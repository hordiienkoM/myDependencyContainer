Один из вариантов установки библиотеки - создать в папке проекта директорию, поместить туда jar файл. 
В  IntellijIdea, подключить можно зайдя в меню File/ProjectStructure/libraries/+/java в выпадающем меню
указать путь к jar файлу библиотеки, подтвердить выбор.

В тестовом примере, есть готовые классы, на которых можно протестировать работу аннотаций.
Работа с контейнером происходит через методы статического класса IoC.
Для корректной работы аннотаций, необходимо инициализировать контейнер, используя методы 
IoC.init(String rootPackage, ClassLoader classLoader) или
init(String rootPackage, ClassLoader classLoader, List<Subscriber> processors).
При их использовании, контейнер через рефлексию считывает классы находящиеся в корневой папке, 
анализирует аннотации и выполняет их обработку.

Задание с комментариями:
Необходимо написать свой контейнер зависимостей с реализацией на аннотациях. (Как в Spring)
Список аннотаций и их описание.
1. Component - применяется к классу. Класс отмеченный этой аннотацией должен быть зарегистрирован в контейнере.

   (К) Если класс помеченн этой аннотацией, контейнер автоматически создает экземпляр класси и регистрирует его. 
Все зависимости внедряются по мере необходимости, но это возможно только если зависимость так помечена аннотацией 
@Component.
   Если нужно зарегестрировать бин у которого в конструкторе есть параметры без этой аннотации, это можно сделать
вручную с помощью метода IoC.registerBean(Object instance). 
   Исключение - интерфейсы, у которых есть наследник с аннотацией @Component. Их тоже загрузит автоматически.
   Получить бин можно с помощью метода IoC.getBean(Class<T> clazz)
2. Autowired - применяется к конструктору. Если в классе несколько конструкторов,
   необходимо оставить возможность отметить, какой конструктор стоит использовать, этой аннотацией.
   В противном случае используется первый доступный конструктор.

   (К) Если не выполняется условие из предидущего пункта, выскочит ошибка.
3. Qualifier - применяется к параметрам конструктора, на тот случай если нужно добавить другой экземпляр класса. Идентифицирует параметр по строке.

   (К) Чтобы работало корректно, значение value() аннотации @Qualifier должно совпадать со значением
соответсвующего value @Component
4. PostConstructor - применяется к методу, который нужно вызвать сразу после создания инстанса.

   (K) Если метод имеет параметры, выбьет ошибку.

Требования:
1. Контейнер должен быть изолированным.

   (К) класс DependencyContainer
2. Доступ к инстансам должен предоставляться методом контейнера, по классу.

   (K) Метод DependencyContainer.DependencyContainer.resolve(Class<T> clazz).  
Исключение, если сам контейнер пытается получить инстанс параметра отмеченного аннотацией @Qualifier. За этот случай
отвечает метод DependencyContainer.resolve(Class<T> clazz, String qualifierValue)
3. Зависимости компонента должны быть автоматически включены в него.

   (К) Метод DependencyContainer.createConstructorParameters
4. Возможность добавлять обработчики перед и после добавления в контейнер

   (К) Чтобы сделать обработчик событий, нужно создать класс, унаследовать его от Subscriber, переопределить метод
handleEvent(Event event), и зарегестрировать в контейнере, вызвав метод IoC.addProcessors(List<Subscriber> processors), 
или инициализировав методом init(String rootPackage, ClassLoader classLoader, List<Subscriber> processors).
В нашем случае у Event есть 2 реализации - PostProcessComponentEvent и PreProcessComponentEvent, которые хранят 
класс Обьекта вызвавшего создание Event. Если нужно сделать обработчик для какой-то определенной реализации, в методе 
handleEvent(Event event) нужно будет добавить проверку:
   event instanceof PostProcessComponentEvent/PreProcessComponentEvent
   Обработчики нужно добавлять до запуска методов IoC.registerBean\init(String rootPackage, ClassLoader classLoader). 
Пример их реализации есть в репозитории testDependencyContainer.
5. Развернутая и понятная информация о том, в каком моменте внедрение зависимостей уходит в круговой цикл

   (K) За это отвечает класс DependencyChecker. Если есть кольцевая зависимость, Class1 (Class2), Class2 (Class1) 
он выдаст ошибку вида:
   Class1 -> Class2 -> Class1
6. Развернутая работа с ClassLoader, чтобы разные загрузчики не ломали реализацию.

   (K) Можно использовать разные загрузчики, для этого используются методы IoC.init(String rootPackage, ClassLoader classLoader),
IoC.init(String rootPackage, ClassLoader classLoader, List<Subscriber> processors)

Примечание:
Вы можете использовать любые библиотеки, однако после сборки ваш итоговый jar-файл не должен превышать 300кб.