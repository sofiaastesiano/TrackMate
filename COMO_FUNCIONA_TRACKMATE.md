# Cómo Funciona TrackMate:

**TrackMate está diseñado para trabajar con videos** - específicamente videos de microscopía time-lapse y secuencias de imágenes. Procesa videos fotograma a fotograma para detectar objetos (como células, partículas o núcleos) y luego enlaza estas detecciones a lo largo del tiempo para crear trayectorias que muestran cómo se mueven y comportan los objetos.

TrackMate sigue un **pipeline de dos etapas**:
1. **Detección (Segmentación)** - Encontrar objetos en cada fotograma independientemente
2. **Seguimiento (Tracking)** - Conectar el mismo objeto a través de múltiples fotogramas

## ¿Qué Tipo de Videos/Imágenes Acepta TrackMate?

TrackMate funciona con formatos de imagen de ImageJ/Fiji y acepta:

- **Videos time-lapse** (2D a lo largo del tiempo)
- **Secuencias 3D time-lapse** (Z-stacks a lo largo del tiempo)
- **Imágenes multicanal** (puede seleccionar qué canal rastrear)
- **Secuencias de imágenes** con dimensión temporal
- **Varios formatos**: TIFF stacks, AVI, QuickTime, OME-TIFF, y cualquier formato que ImageJ pueda abrir

**Estructura de Imagen:**
- Dimensiones: Ancho × Alto × Planos-Z × Fotogramas de tiempo × Canales
- Calibración: Unidades físicas (µm, segundos) para mediciones precisas
- ROI opcional: Puede limitar el procesamiento a regiones espaciales específicas

## ¿Qué Objetos Puede Rastrear TrackMate?

TrackMate está optimizado para **objetos tipo punto** - cosas que aparecen como regiones brillantes sobre un fondo oscuro:

**Ideal para:**
- Partículas/perlas fluorescentes
- Puntos fluorescentes de sub-resolución
- Núcleos celulares (a resolución baja-media)
- Vesículas y organelos
- Células individuales (a baja resolución)
- Virus o bacterias (si son visibles como puntos)
- Cualquier objeto brillante, aproximadamente circular

**No es adecuado para:**
- Objetos grandes donde el contorno de la forma es crítico
- Objetos muy tenues con poco contraste
- Objetos muy superpuestos (a menos que se usen detectores especializados)
- Objetos sobre fondos brillantes

## Etapa 1: Detección (Encontrar Objetos en Cada Fotograma)

La detección ocurre **independientemente para cada fotograma**. TrackMate ofrece varios algoritmos de detección, cada uno adecuado para diferentes tipos de objetos:

### Algoritmo de Detección 1: Detector DoG (Diferencia de Gaussianas)

**Cómo funciona:**
1. Aplicar dos filtros Gaussianos con radios ligeramente diferentes (σ₁ y σ₂)
2. Restar las dos imágenes desenfocadas
3. Esto resalta estructuras tipo blob a una escala de tamaño específica
4. Encontrar máximos locales (puntos brillantes) por encima de un umbral
5. Refinar posiciones con precisión sub-píxel

**Enfoque matemático:**
```
DoG = FiltroGaussiano(imagen, σ₁) - FiltroGaussiano(imagen, σ₂)
donde σ₁ = radio/√ndims × 0.9 y σ₂ = radio/√ndims × 1.1
```

**Nota:** las dos constantes son factores de escala.

**Mejor para:** Puntos pequeños y brillantes (< 5 píxeles de radio)
**Velocidad:** Detector más rápido
**Configuraciones:** Radio estimado del punto, umbral de calidad

#### Detalles de Implementación del Filtro Gaussiano

**Fórmula matemática del filtro Gaussiano:**

El filtro Gaussiano se basa en la función Gaussiana 2D:

```
G(x, y) = (1 / 2πσ²) × e^(-(x² + y²) / 2σ²)
```

Donde:
- `σ` (sigma) = desviación estándar que controla el ancho del desenfoque
- `x, y` = coordenadas relativas al centro del kernel

**Implementación optimizada con convolución separable:**

TrackMate NO aplica el kernel 2D/3D directamente. En su lugar, usa **convolución separable** a través de la biblioteca ImgLib2's `Gauss3.gauss()`, que es mucho más eficiente:

**Propiedad de separabilidad:**
```
Gaussiana 2D = Gaussiana 1D (horizontal) × Gaussiana 1D (vertical)

G(x, y) = G(x) × G(y)
donde G(x) = e^(-x² / 2σ²)
```

**Ventajas de la convolución separable:**

| Dimensión | Convolución Directa | Convolución Separable | Reducción |
|-----------|---------------------|----------------------|-----------|
| 2D | O(n²) multiplicaciones/píxel | O(2n) multiplicaciones/píxel | ~50% para n=5 |
| 3D | O(n³) multiplicaciones/píxel | O(3n) multiplicaciones/píxel | ~95% para n=10 |

**Ejemplo concreto:**
- Kernel 5×5 directo: 25 multiplicaciones por píxel
- Separable (5×1 + 1×5): 10 multiplicaciones por píxel

### Algoritmo de Detección 2: Detector LoG (Laplaciano de Gaussiana)

**Cómo funciona:**
1. Convolucionar la imagen con un kernel Laplaciano de Gaussiana
2. El kernel LoG está ajustado al tamaño de blob esperado
3. Usa FFT (Transformada Rápida de Fourier) para cómputo eficiente
4. Encontrar máximos locales en el resultado filtrado
5. Localización sub-píxel para precisión

**Base matemática:**
```
LoG = ∇²(G * I) donde G es Gaussiana, I es imagen
```

**Mejor para:** Detección de blobs de propósito general, puntos de tamaño medio
**Velocidad:** Moderada (más lenta que DoG, usa FFT)
**Configuraciones:** Radio estimado del punto, umbral de calidad

**Propiedades del filtro LoG:**
**Invariante a escala**: El kernel se ajusta automáticamente al tamaño del blob (σ)
**Selectivo de tamaño**: Respuesta máxima para blobs de radio ≈ σ√2
**Supresión de ruido**: El componente Gaussiano suaviza el ruido de alta frecuencia
**Respuesta simétrica**: Igual respuesta en todas las orientaciones
**Sensible a contraste**: Detecta transiciones de intensidad (bordes y blobs)

**Comparación DoG vs LoG:**

| Aspecto | DoG | LoG |
|---------|-----|-----|
| **Implementación** | Dos desenfoque Gaussiano + sustracción | Kernel LoG + convolución |
| **Cálculo** | Espacio directo (separable) | Espacio de Fourier (FFT) |
| **Velocidad para σ pequeño** | Más rápido | Moderado |
| **Velocidad para σ grande** | Moderado |  Más rápido |
| **Precisión matemática** | ~Aproximación del LoG | Exacto |
| **Uso de memoria** | Dos imágenes temporales | Una imagen FFT compleja |
| **Mejor para** | Puntos pequeños (< 5px) | Puntos medianos/grandes |


### Algoritmo de Detección 3: Detector Hessiano

**Cómo funciona:**
1. Calcular la matriz Hessiana (matriz de segundas derivadas) en cada píxel
2. Calcular el determinante de la Hessiana
3. Determinante positivo indica estructuras tipo blob
4. Puede manejar diferentes radios en X/Y vs Z (puntos anisotrópicos)

**Matriz Hessiana (2D):**
```
H = [[∂²I/∂x², ∂²I/∂x∂y  ]
     [∂²I/∂x∂y, ∂²I/∂y²]]
```

**Mejor para:** Puntos elípticos, objetos anisotrópicos, orientaciones específicas
**Velocidad:** Más lento, más intensivo computacionalmente
**Configuraciones:** Radio en XY, radio en Z, umbral de calidad

**Fundamento matemático:**

La **matriz Hessiana** es la matriz de segundas derivadas parciales de una función (en este caso, la imagen):

**Matriz Hessiana 2D:**
```
H = [[∂²I/∂x²,   ∂²I/∂x∂y  ]
     [∂²I/∂x∂y,  ∂²I/∂y²  ]]
```

**Matriz Hessiana 3D:**
```
H = [[∂²I/∂x²,   ∂²I/∂x∂y,  ∂²I/∂x∂z]
     [∂²I/∂x∂y,  ∂²I/∂y²,   ∂²I/∂y∂z]
     [∂²I/∂x∂z,  ∂²I/∂y∂z,  ∂²I/∂z² ]]
```

**Propiedad clave:** El determinante de la Hessiana indica la curvatura local de la imagen:
- **det(H) > 0**: Máximo o mínimo local (blob)
- **det(H) < 0**: Punto de silla (borde)
- **det(H) ≈ 0**: Región plana


**Ventajas del detector Hessiano:**

**Mejor eliminación de respuesta de bordes** que LoG (Mikolajczyk et al., 2005)
**Detección anisotrópica**: Radios diferentes en XY y Z para blobs elípticos
**Adecuado para imágenes con bordes fuertes**: No confunde bordes con blobs
**Invariante a rotación**: Respuesta igual en todas las orientaciones
**Normalización por escala**: σ-normalización para detectar múltiples tamaños
**Multi-hilo optimizado**: Procesamiento paralelo de píxeles

**Desventajas:**

**Más lento** que DoG y LoG (más cálculos)
**Mayor uso de memoria**: Almacena matriz Hessiana completa
**Sensible a ruido**: Usa segundas derivadas (más ruidosas que primeras)



### Flujo de Trabajo Común de Detección (Todos los Algoritmos)

```
Para cada fotograma de tiempo:
  1. Opcional: Aplicar filtro de mediana para reducir ruido
  2. Aplicar metodo de detección (DoG/LoG/Hessiano) ajustado al tamaño esperado del objeto
  3. Encontrar máximos locales en la imagen filtrada
  4. Umbralizar por calidad (brillo/respuesta)
  5. Opcional: Localización sub-píxel 
  6. Crear objetos Spot con:
     - Posición (X, Y, Z en unidades calibradas)
     - Radio
     - Puntuación de calidad
     - Número de fotograma
```

**Parámetros Clave:**
- **Radio**: Tamaño esperado de los objetos (en unidades físicas o píxeles)
- **Umbral**: Calidad/intensidad mínima para detección
- **Localización sub-píxel**: Mejorar precisión de posición más allá de la cuadrícula de píxeles
- **Filtrado de mediana**: Reducir ruido antes de la detección

### Salida de la Detección

Cada objeto detectado se convierte en un **Spot** con:
- **Posición**: Coordenadas X, Y, Z (en unidades calibradas como µm)
- **Tiempo**: Número de fotograma y marca temporal
- **Radio**: Tamaño estimado del objeto
- **Calidad**: Puntuación de confianza de detección
- **Características**: Calculadas más adelante (intensidad, forma, etc.)

## Etapa 2: Seguimiento (Enlazar Objetos a Través de Fotogramas)

Después de que la detección encuentra objetos en cada fotograma independientemente, el seguimiento los conecta para formar trayectorias. El desafío principal: **¿Qué objeto en el fotograma t+1 corresponde a qué objeto en el fotograma t?**

### Algoritmo de Seguimiento 1: Tracker LAP (Problema de Asignación Lineal)

**Basado en:** Jaqaman et al., Nature Methods, 2008

El tracker LAP usa **optimización global** para encontrar la mejor asignación posible de puntos a través de fotogramas.

#### Paso 1: Enlace Fotograma a Fotograma

**Cómo funciona:**

1. **Construir una matriz de costos**:
   - Filas = puntos en el fotograma t
   - Columnas = puntos en el fotograma t+1
   - Cada celda = costo de enlazar el punto i al punto j

2. **Función de costo** (típicamente distancia al cuadrado):
   ```
   Costo(punto_i, punto_j) = distancia²(punto_i, punto_j)
   ```
   - Puede incluir penalizaciones por diferencias de características (tamaño, intensidad)
   - Costo infinito si distancia > distancia máxima de enlace

3. **Resolver el Problema de Asignación Lineal**:
   - Encontrar la correspondencia óptima uno-a-uno que minimiza el costo total
   - Usa el algoritmo de Jonker-Volgenant (eficiente para problemas grandes)
   - Formulación de matriz dispersa maneja miles de puntos

4. **Resultado**: Cada punto se enlaza a como máximo un punto en el siguiente fotograma

**Parámetros Clave:**
- **Distancia máxima de enlace**: Distancia máxima que una partícula puede moverse entre fotogramas
- **Penalizaciones de características**: Ponderar diferencias en intensidad, tamaño, etc.

#### Paso 2: Cierre de Brechas, División y Fusión

Después de que el enlace fotograma a fotograma crea segmentos de trayectoria, este paso maneja eventos complejos:

**1. Cierre de Brechas** - Reconectar trayectorias rotas
- Las detecciones pueden fallar durante algunos fotogramas (partícula fuera de foco, tenue, etc.)
- Enlaza segmentos de trayectoria separados por detecciones faltantes
- Límites de distancia y tiempo máximos previenen conexiones incorrectas

**2. División de Trayectorias** - Manejar división celular
- Una trayectoria se convierte en dos trayectorias
- Útil para células que se dividen o partículas que se fragmentan

**3. Fusión de Trayectorias** - Manejar eventos de fusión
- Dos trayectorias se convierten en una trayectoria
- Útil para células que se fusionan o partículas que se agregan

**Formulado como otro LAP:**
- Todos los segmentos de trayectoria (puntos de inicio, puntos finales)
- Todas las conexiones posibles (cierre de brecha, división, fusión)
- Costos alternativos para nacimiento (aparece nuevo objeto) y muerte (objeto desaparece)
- La optimización global encuentra el mejor escenario

**Parámetros Clave:**
- **Distancia máxima de cierre de brecha**: Qué tan lejos puede moverse el objeto durante la brecha
- **Brecha máxima de fotogramas**: Cuántos fotogramas se pueden saltar
- **Permitir división**: Habilitar/deshabilitar eventos de división
- **Permitir fusión**: Habilitar/deshabilitar eventos de fusión

### Algoritmo de Seguimiento 2: Tracker Kalman

**Cómo funciona:**

1. **Predecir** siguiente posición basándose en posición y velocidad actual
2. **Medir** posición real en el siguiente fotograma
3. **Actualizar** predicción usando filtro de Kalman (combinación ponderada)
4. Enlazar al punto más cercano dentro del radio de búsqueda

**Modelo de Movimiento:**
```
Posición(t+1) = Posición(t) + Velocidad(t) × Δt
```

**Mejor para:** Objetos con movimiento suave y predecible
**Velocidad:** Muy rápido (codicioso, no optimización global)
**Limitación:** Puede fallar con cambios bruscos de dirección o campos densos

### Salida del Seguimiento

La etapa de seguimiento produce una **estructura de grafo**:
- **Nodos** = Spots (detecciones individuales)
- **Aristas** = Enlaces entre puntos en fotogramas consecutivos
- **Trayectorias** = Caminos conectados a través del grafo (trayectorias)

Cada trayectoria representa el viaje de un objeto a través del tiempo y el espacio.

## Etapa 3: Cálculo de Características

Después de la detección y el seguimiento, TrackMate calcula características numéricas para el análisis:

### Características de Puntos
Calculadas para cada detección individual:
- **Intensidad**: Media, máxima, mínima, mediana, intensidad total
- **Morfología**: Radio estimado, excentricidad (si el contorno 2D está disponible)
- **SNR**: Relación señal-ruido
- **Posición**: Coordenadas X, Y, Z
- **Personalizadas**: Extensibles mediante plugins

### Características de Aristas
Calculadas para cada enlace entre puntos:
- **Velocidad**: Desplazamiento / tiempo
- **Desplazamiento**: Distancia movida
- **Ángulo**: Dirección del movimiento
- **Aceleración**: Cambio en velocidad

### Características de Trayectorias
Calculadas para trayectorias enteras:
- **Duración**: Cuánto tiempo dura la trayectoria
- **Desplazamiento total**: Distancia de inicio a fin
- **Longitud de trayectoria**: Suma de todos los desplazamientos
- **Velocidad media**: Velocidad promedio sobre la trayectoria
- **Ratio de confinamiento**: Desplazamiento / longitud de trayectoria (indicador de difusión)
- **Ramificación**: Número de divisiones
- **Brechas**: Número de fotogramas faltantes

## Pipeline Completo

```
1. CARGAR VIDEO/SECUENCIA DE IMÁGENES
   ↓
2. DETECCIÓN (por fotograma)
   ├─> Aplicar detector (DoG/LoG/Hessiano)
   ├─> Encontrar puntos brillantes por encima del umbral
   └─> Crear objetos Spot
   ↓
3. FILTRADO INICIAL
   └─> Eliminar detecciones de baja calidad
   ↓
4. CALCULAR CARACTERÍSTICAS DE PUNTOS
   └─> Intensidad, morfología, SNR, etc.
   ↓
5. FILTRADO DE PUNTOS
   └─> Filtrar por valores de características (tamaño, intensidad, etc.)
   ↓
6. SEGUIMIENTO
   ├─> Enlace fotograma a fotograma (LAP/Kalman)
   ├─> Cierre de brechas
   ├─> Manejar divisiones/fusiones
   └─> Construir grafo de trayectorias
   ↓
7. CALCULAR CARACTERÍSTICAS DE ARISTAS
   └─> Velocidad, desplazamiento, ángulo
   ↓
8. CALCULAR CARACTERÍSTICAS DE TRAYECTORIAS
   └─> Duración, desplazamiento total, confinamiento
   ↓
9. FILTRADO DE TRAYECTORIAS
   └─> Filtrar por propiedades de trayectorias
   ↓
10. VISUALIZACIÓN Y ANÁLISIS
    ├─> Superponer trayectorias en video
    ├─> Graficar características a lo largo del tiempo
    ├─> Exportar datos (CSV, XML, MATLAB)
    └─> Análisis estadístico
```

## Ejemplo: Seguimiento de Células en un Video Time-Lapse

**Escenario:** Tienes un video 2D time-lapse de células fluorescentes (100 fotogramas, células ~10 píxeles de diámetro)

**Flujo de Trabajo:**

1. **Abrir video en Fiji/ImageJ**
   - Cargar tu time-lapse (File → Open)
   - Verificar calibración (Image → Properties)

2. **Iniciar TrackMate**
   - Plugins → Tracking → TrackMate
   - Seleccionar tu imagen

3. **Configurar Detección**
   - Elegir detector: **Detector LoG** (buena opción general)
   - Establecer radio: **5 píxeles** (mitad del diámetro de la célula)
   - Establecer umbral de calidad: Ajustar basándose en vista previa

4. **Ejecutar Detección**
   - TrackMate escanea los 100 fotogramas
   - Encuentra puntos brillantes en cada fotograma
   - La vista previa muestra puntos detectados superpuestos

5. **Filtrar Puntos** (opcional)
   - Eliminar puntos con baja intensidad
   - Eliminar puntos en los bordes de la imagen
   - Eliminar puntos con tamaño incorrecto

6. **Configurar Seguimiento**
   - Elegir tracker: **Tracker LAP**
   - Distancia máxima de enlace: **15 píxeles** (las células no deberían moverse >15px por fotograma)
   - Cierre de brecha máximo: **2 fotogramas**
   - Permitir división: **Sí** (las células pueden dividirse)

7. **Ejecutar Seguimiento**
   - El solver LAP conecta puntos a través de fotogramas
   - Maneja fallos temporales de detección
   - Detecta divisiones celulares

8. **Filtrar Trayectorias** (opcional)
   - Mantener solo trayectorias más largas de 10 fotogramas
   - Eliminar trayectorias cortas espurias

9. **Visualizar**
   - Ver trayectorias superpuestas en video
   - Colorear por velocidad, ID de trayectoria o tiempo
   - Generar gráficos de trayectorias

10. **Exportar y Analizar**
    - Exportar datos de trayectorias a CSV
    - Gráficos estadísticos (distribución de velocidad, duración de trayectoria)
    - Análisis adicional en MATLAB/Python

## Ventajas Técnicas

### 1. Multi-threading
- Múltiples fotogramas procesados simultáneamente
- Cálculo de características en paralelo
- Escala con núcleos de CPU

### 2. Precisión Sub-Píxel
- Ajuste cuadrático para refinamiento de posición
- Logra precisión de localización <0.1 píxel

### 3. Optimización Global (LAP)
- Considera todas las asignaciones posibles
- Más robusto que el vecino más cercano codicioso
- Maneja escenarios complejos (campos densos, fusión/división)

### 4. Arquitectura Modular
- Intercambiar algoritmos de detección fácilmente
- Intercambiar algoritmos de seguimiento fácilmente
- Agregar calculadores de características personalizados
- Extensible mediante plugins

### 5. Flujo de Trabajo Interactivo
- Vista previa de detección en fotogramas de muestra
- Ajustar parámetros en tiempo real
- Herramientas de corrección manual
- Retroalimentación de visualización inmediata

## Casos de Uso Comunes

1. **Estudios de Migración Celular**
   - Rastrear núcleos celulares en ensayos de cicatrización de heridas
   - Medir velocidad y direccionalidad de migración

2. **Difusión de Partículas**
   - Rastrear perlas fluorescentes
   - Calcular desplazamiento cuadrático medio
   - Determinar coeficientes de difusión

3. **Transporte Intracelular**
   - Rastrear vesículas u organelos
   - Analizar velocidad y dirección del transporte

4. **Análisis de División Celular**
   - Rastrear células que se dividen
   - Medir tasas de división y árboles de linaje

5. **Seguimiento Bacteriano**
   - Rastrear bacterias individuales
   - Analizar crecimiento y motilidad

## Limitaciones

- **Solo objetos tipo punto**: No es adecuado para formas irregulares
- **Requisito de contraste**: Necesita objetos brillantes sobre fondo oscuro
- **Oclusión**: Tiene dificultades con objetos muy superpuestos
- **Cambios de apariencia**: Asume que los objetos no cambian drásticamente de apariencia
- **Tiempo de cómputo**: Videos grandes (muchos fotogramas, muchos objetos) pueden ser lentos

## Resumen

TrackMate transforma videos time-lapse en datos de seguimiento cuantitativos a través de:

1. **Detección inteligente** usando filtrado de espacio-escala (DoG/LoG/Hessiano) para encontrar puntos a la escala de tamaño correcta
2. **Seguimiento robusto** usando optimización global (framework LAP) para manejar escenarios complejos
3. **Características ricas** extrayendo mediciones cuantitativas de trayectorias
4. **Flujo de trabajo flexible** con vista previa, filtrado y corrección manual
5. **Diseño extensible** permitiendo algoritmos y análisis personalizados

La separación de detección y seguimiento permite la optimización independiente de cada etapa, y la arquitectura de plugins hace que TrackMate sea adaptable a diversas aplicaciones de imagen biológica.
