# TrackMate - Proyecto Académico

Este repositorio es un fork del proyecto original TrackMate, que implementa una plataforma extensible para detección y seguimiento de objetos (blobs) en imágenes y vídeos de microscopía. El código base y la arquitectura de plugins (SciJava) pertenecen al repositorio original de TrackMate, que debe considerarse como la fuente principal de la implementación.

### Alcance del trabajo en este fork

En este trabajo no se reimplementa TrackMate completo, sino que se toma como base el código existente y se realizan aportaciones puntuales centradas en:

1. Detector LoG
    - En la implementación original de TrackMate, la convolución entre la imagen y el kernel LoG se realiza mediante FFT.
    - Nuestra principal aportación de código es la implementación de una versión alternativa de la convolución, llamada DirectConvolution, que permite comparar los diferentes métodos
    - El resto de la lógica del detector LoG (generación del kernel, normalización, integración en la arquitectura de plugins, etc.) se mantiene tal como está en el repositorio original.

2. LAP Tracker
    - El LAP Tracker utilizado en este trabajo es el que ya venía implementado en el repositorio original de TrackMate.
    - En este fork no se han realizado modificaciones en el código del tracker: nuestro uso del LAP Tracker se limita a la configuración y experimentación con parámetros sobre la implementación existente.

3. Archivos adicionales para ejecutar TrackMate como aplicación
    - Al clonar el repositorio original, TrackMate no era seleccionable directamente como plugin al ejecutarlo desde el proyecto, sin pasar por Fiji.
    - Para poder compilar y ejecutar TrackMate de forma autónoma, se añadieron algunos archivos de soporte que facilitan lanzar la interfaz de trackmate con un video ya cargado.
    - Estas clases y configuraciones son meramente de soporte y no modifican la lógica interna de los algoritmos originales.