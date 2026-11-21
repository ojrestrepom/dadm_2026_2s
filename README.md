# Desarrollo de Aplicaciones para Dispositivos Móviles (DADM)

Repositorio académico de los retos prácticos del curso **Desarrollo de Aplicaciones para Dispositivos Móviles**, de la **Universidad Nacional de Colombia**.

Aquí se organiza el código fuente de las aplicaciones Android desarrolladas para explorar interfaces gráficas, lógica de juego, persistencia de datos, servicios en la nube, ubicación geográfica, consumo de APIs e integración con inteligencia artificial generativa.

## Organización del repositorio

Cada reto se encuentra en una carpeta independiente, identificada con el formato `Reto_X`.

> **Nota:** Los retos 1 y 2 forman parte del proyecto **Boticario AI** y, por tanto, no se incluyen en este repositorio.

## Retos

| Reto | Tema | Descripción |
| --- | --- | --- |
| [Reto 3](./Reto_3) | **Triqui: jugador contra Android** | Construcción de un juego de triqui (Tic-Tac-Toe) con tablero de 3 × 3, turnos entre el usuario y la máquina, detección de victorias y empates, y separación entre lógica e interfaz. |
| [Reto 4](./Reto_4) | **Menús y cuadros de diálogo** | Ampliación del triqui con niveles de dificultad de la máquina (fácil, intermedio y experto), menú de opciones, confirmación de salida, pantalla «Acerca de» e icono personalizado. |
| [Reto 5](./Reto_5) | **Gráficos y sonido** | Personalización del tablero mediante una vista gráfica propia, dibujo de las fichas X y O, detección de pulsaciones táctiles y reproducción de efectos de sonido. |
| [Reto 6](./Reto_6) | **Orientación y persistencia del estado** | Adaptación del juego a orientaciones vertical y horizontal, conservación de la partida ante cambios de orientación y almacenamiento persistente de resultados y preferencias. |
| [Reto 7](./Reto_7) | **Triqui en línea** | Extensión del juego para dos jugadores en dispositivos diferentes, con creación y unión a partidas y sincronización mediante un servicio en la nube, como Firebase. |
| [Reto 8](./Reto_8) | **Almacenamiento con SQLite** | Aplicación de directorio de empresas de software con operaciones CRUD, consulta y filtrado por nombre o clasificación, y confirmación antes de eliminar registros. |
| [Reto 9](./Reto_9) | **GPS y mapas** | Aplicación basada en la ubicación del dispositivo que presenta puntos de interés cercanos sobre un mapa, con un radio de búsqueda configurable. |
| [Reto 10](./Reto_10) | **Consumo de servicios web** | Aplicación Android que consulta una API web, envía parámetros de búsqueda, procesa respuestas JSON y muestra los resultados en una interfaz gráfica. |
| [Reto 11](./Reto_11) | **Inteligencia artificial generativa** | Integración de una aplicación Android con un modelo de IA generativa para funcionalidades como conversación, generación de texto o generación de imágenes. |

## Tecnologías y conceptos abordados

- **Android Studio**, **Java** y diseños de interfaz con **XML**.
- Componentes de interfaz, eventos táctiles, menús y diálogos.
- Ciclo de vida de actividades, cambios de orientación y preferencias.
- Persistencia local con **SQLite**.
- Comunicación entre dispositivos y servicios en la nube, incluido **Firebase**.
- Geolocalización, mapas, solicitudes HTTP y procesamiento de **JSON**.
- Integración de servicios de **inteligencia artificial generativa**.

Las tecnologías específicas pueden variar entre retos, según la solución implementada.

## Cómo explorar los proyectos

1. Ingresa a la carpeta del reto que deseas consultar.
2. Abre el proyecto Android correspondiente en **Android Studio**.
3. Espera la sincronización de **Gradle** y revisa los requisitos de configuración particulares del proyecto.
4. Ejecuta la aplicación en un emulador o dispositivo Android compatible.

**Importante:** Algunos retos pueden requerir configuración adicional de servicios externos, permisos del dispositivo o credenciales propias. Los archivos con claves privadas y otros secretos no deben publicarse en el repositorio.

## Contexto académico

Los retos 3 a 6 siguen una secuencia de ampliaciones del juego *Tic-Tac-Toe*, a partir de material didáctico de **Frank McCown (Harding University)**. Los retos posteriores abordan integración en la nube, bases de datos, localización, servicios web e inteligencia artificial, de acuerdo con las guías del curso.

Este repositorio tiene fines **académicos y de aprendizaje**. Las descripciones anteriores resumen los objetivos de las guías; el código de cada carpeta permite consultar la implementación correspondiente.
