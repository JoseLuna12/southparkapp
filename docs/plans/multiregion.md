# Plan de soporte multirregión

## Alcance y conclusión

La misma app podría ofrecer un selector de fuente regional. Elegir "Latinoamérica · español latinoamericano" cargaría el catálogo de la fuente oficial LATAM y usaría los idiomas que esa fuente entregue. No cambiaría la ubicación del usuario ni garantizaría acceso desde otro territorio.

El selector y guardar la preferencia tienen complejidad baja. El soporte completo tiene complejidad media si LATAM comparte el catálogo y el protocolo de reproducción actuales. Si cambia el proveedor, la autenticación, la publicidad o el DRM, deja de ser una configuración de URL y necesita investigación adicional. No se puede confirmar el caso simple sin examinar la fuente regional.

Este documento propone trabajo futuro. No implementa la función ni modifica el player, los streams o los controles de acceso.

## Evidencia y límites de esta investigación

La revisión del 31 de octubre de 2026 se basa en el código y la documentación del repositorio. No se hicieron peticiones a fuentes regionales, pruebas de reproducción, instalaciones ni cambios de red. No se ha confirmado un dominio, una ruta de catálogo o un endpoint LATAM; no se presenta ninguno como operativo.

Fuentes internas revisadas:

- [AGENTS.md](../../AGENTS.md), [README.md](../../README.md), [protocolo oficial](../site-protocol.md) y [validación](../../VALIDATION.md) fijan los límites de acceso y la evidencia previa.
- [Catalog.java](../../app/src/main/java/com/unofficial/southparktv/Catalog.java), especialmente `BASE`, `officialPage`, `seasons`, `episodes` y `seasonPage`, define el catálogo y sus rutas admitidas.
- [SiteClient.java](../../app/src/main/java/com/unofficial/southparktv/SiteClient.java), especialmente `seasons`, `episodes`, `playback` y `download`, concentra las solicitudes y las validaciones de proveedor.
- [WatchHistory.java](../../app/src/main/java/com/unofficial/southparktv/WatchHistory.java) y [MainActivity.java](../../app/src/main/java/com/unofficial/southparktv/MainActivity.java) muestran cómo se guardan el historial y la temporada, y cómo se descartan respuestas antiguas.
- [PlayerActivity.java](../../app/src/main/java/com/unofficial/southparktv/PlayerActivity.java) se leyó para entender el contexto de sesión y las pistas nativas. No se propone rediseñarlo.
- [CatalogTest.java](../../app/src/test/java/com/unofficial/southparktv/CatalogTest.java) y [SiteClientTest.java](../../app/src/test/java/com/unofficial/southparktv/SiteClientTest.java) muestran casos existentes de URL, catálogo y siguiente episodio.

Las cifras y pruebas descritas en README y VALIDATION son evidencia anterior, no resultados reproducidos durante esta investigación. Esa evidencia no confirma otras regiones.

## Cómo están acoplados hoy región, idioma y fuente

Existe un único cliente `SiteClient`, sin perfil regional. `Catalog.BASE` es `https://www.southparkstudios.com`. `SiteClient.seasons()` empieza en `/seasons/south-park`, y el parser espera temporadas bajo ese prefijo y episodios bajo `/episodes/`. La paginación espera `/api/context/.../episode/{página}/{tamaño}`. Se leen datos de `window.__DATA__`, no una API pública con contrato garantizado.

La región efectiva depende de lo que el proveedor permita a la red y sesión reales. La app no envía una región elegida ni cambia el país que devuelve el servicio. Los títulos y descripciones llegan del catálogo oficial. El audio y los subtítulos dependen de las pistas entregadas en reproducción. Cambiar el idioma de Android o traducir los botones no cambia por sí mismo la fuente ni consigue doblaje latinoamericano.

Las restricciones actuales están en varios puntos:

- `Catalog.officialPage()` resuelve las páginas contra el dominio fijo y exige HTTPS, host oficial exacto, ausencia de credenciales en la URL y puerto predeterminado o 443.
- `SiteClient.download()` admite únicamente `www.southparkstudios.com`, `topaz.paramount.tech` e `images.paramount.tech`. Comprueba cada salto de redirección, HTTPS, credenciales y puerto. Esta lista corresponde a sus descargas de datos e imágenes, no a todos los destinos internos de Media3 o IMA.
- `SiteClient.playback()` acepta el `videoServiceUrl` actual solo si usa HTTPS, `topaz.paramount.tech` y el prefijo `/topaz/api/`. Solicita una sesión nueva `androidtv` y solo admite la respuesta HLS con `googledai` e `imasdk` esperada.
- Las entradas bloqueadas y `authRequired` detienen el acceso. La app no implementa login del proveedor. Mantiene los parámetros publicitarios suministrados y no fabrica consentimiento.

No basta con sustituir `BASE`: los filtros de rutas, la paginación, las redirecciones, el servicio y el referer oficial también asumen esa fuente. El historial añade otro acoplamiento. Guarda posiciones por ID de episodio y una entrada global `lastEpisode`, sin región. La temporada seleccionada tampoco está separada por región. No hay garantía de que dos catálogos compartan IDs, rutas, cortes o duraciones.

## Diseño propuesto

### Selector de fuente oficial

Añadir posteriormente un selector accesible con D-pad en el catálogo, usando los componentes y recursos existentes. Cada opción identificaría la fuente regional y el idioma esperado del contenido. La fuente actual seguiría como valor inicial para preservar el comportamiento existente. LATAM solo aparecería como opción compatible tras verificarla.

La relación propuesta sería:

`región elegida → perfil de fuente oficial → catálogo y detalle regionales → sesión oficial nueva → idiomas disponibles`

Un perfil pequeño e inmutable bastaría para empezar. Definiría un ID estable, nombre visible, origen HTTPS oficial, ruta inicial del catálogo, reglas exactas de hosts y rutas por finalidad, y el protocolo de catálogo y reproducción comprobado. Podría indicar un idioma de contenido preferido, como `es-419`, sin prometer que todas las pistas estén etiquetadas así o existan. No contendría tokens, manifiestos ni credenciales persistidas.

La primera implementación debería reutilizar el parser y `SiteClient` cuando los contratos coincidan. Solo una diferencia observada justificaría un adaptador regional. No hace falta un sistema de plugins, otro framework ni un backend para guardar una preferencia y elegir entre fuentes verificadas.

### Catálogo, sesión e idioma coherentes

Todas las operaciones de una navegación usarían el mismo perfil: temporadas, páginas siguientes, detalles, imágenes permitidas, enlaces oficiales y contexto de reproducción. Un episodio tendría identidad compuesta por fuente e ID oficial. El perfil también tendría que acompañar al lanzamiento y la restauración de reproducción, para que una ruta LATAM nunca se resuelva contra la fuente anterior.

El servicio de reproducción seguiría saliendo de la página oficial del episodio. No se construiría una URL de stream a partir de la región. Si LATAM entrega el mismo contrato compatible, se conservaría el flujo Media3/IMA existente con sesión nueva. Una respuesta incompatible produciría un error explícito o la apertura opcional del sitio oficial, sin probar otra región automáticamente.

Deben distinguirse tres conceptos:

- La fuente regional decide qué catálogo y derechos ofrece el proveedor.
- El idioma del contenido depende del doblaje y de las pistas publicadas para cada episodio. Una preferencia de audio solo puede seleccionar una pista existente.
- El idioma de la interfaz pertenece a los recursos Android. Traducir botones sería un alcance independiente.

Los subtítulos seguirían usando la selección nativa y las preferencias de accesibilidad. Elegir LATAM no debe activar subtítulos inexistentes, traducirlos artificialmente ni asegurar doblaje donde solo haya audio original.

### Cambio de región e historial

Para mantener el alcance inicial pequeño, el cambio se ofrecería desde el catálogo, después de salir de reproducción por el flujo normal que guarda y libera la sesión. No se cambiaría de fuente dentro de un episodio en curso.

Al cambiar, se cancelarían las solicitudes pendientes y se invalidarían las generaciones de catálogo y recomendaciones. Las respuestas e imágenes atrasadas no podrían repoblar la nueva selección. Se recargarían temporadas y episodios, se recuperaría el foco y se conservarían Retry y los estados de temporadas con clips solamente.

La preferencia de temporada, las posiciones, Continue y la última entrada se separarían por fuente. Los datos anteriores se asignarían a la fuente actual sin borrarlos. No se transferiría progreso entre regiones por coincidencia de título o número de episodio. Los catálogos pueden tener IDs y montajes diferentes.

Next y autoplay seguirían el orden del catálogo seleccionado, incluyendo límites de temporada y entradas bloqueadas. No saltarían una entrada restringida para encontrar otra reproducible ni cambiarían de región. Las cachés y los datos restaurados conservarían la identidad de fuente cuando su contenido dependa de ella.

## Complejidad estimada

Estimación cualitativa por alcance y dependencias, no horas medidas ni compromiso de entrega.

| Parte | Complejidad prevista | Qué determina el esfuerzo |
| --- | --- | --- |
| Selector y preferencia persistente | Baja | Una opción de catálogo con foco y recursos existentes. |
| Perfil regional y URL gates | Media | Sustituir supuestos repartidos sin abrir hosts arbitrarios ni aceptar redirecciones fuera del perfil. |
| Catálogo LATAM | Baja a media si comparte contrato | Verificar JSON, rutas, paginación, etiquetas y temporadas de clips. Un esquema distinto requiere adaptación. |
| Historial y cambio seguro | Media | Separar identidades, migrar datos existentes y evitar respuestas antiguas al cambiar. |
| Reproducción con el mismo contrato | Media, condicionada a evidencia | Reutilizar la integración actual y comprobar sesiones nuevas, errores y continuidad regional. |
| Ads y consentimiento | Media a alta | Cada fuente puede variar parámetros, requisitos y entrega. Que un episodio arranque no prueba un corte publicitario. |
| Autenticación | Alta si requiere integración nueva | Hoy se detiene el acceso con login. Ese límite puede mantenerse; implementar login sería otro alcance autorizado. |
| DRM | Alta o incompatibilidad | Depende de servicio, licencias y dispositivo. Una región que exija un flujo no compatible debe quedar sin soporte. |
| Disponibilidad de episodios | Media | Catálogos diferentes, bloqueos, clips y derechos variables. Listado no significa reproducción autorizada. |
| Audio y subtítulos | Baja si las pistas existentes bastan; media si difieren formatos | Comprobar idioma real, etiquetas, cues y menús. La app no puede suministrar pistas ausentes. |

Mi recomendación es empezar por dos fuentes y reutilizar el flujo actual únicamente si la inspección confirma compatibilidad. El trabajo sencillo es elegir y recordar la fuente. El esfuerzo principal está en demostrar que esa elección conserva las condiciones oficiales y no mezcla catálogo, historial o sesión.

## Riesgos y límites

Seleccionar una fuente expresa una preferencia de catálogo, no una autorización territorial. El proveedor conserva la decisión según ubicación real, derechos, cuenta y dispositivo. Si bloquea el acceso, la app debe explicar el resultado y permitir volver al catálogo o al sitio oficial.

No se propone VPN, proxy, falsificación de país, cabeceras de ubicación, credenciales capturadas, eliminación de anuncios, fuentes alternativas ni bypass de DRM o autenticación. Tampoco se reintenta un bloqueo en otras regiones para conseguir reproducción.

Las listas permitidas seguirían siendo explícitas por perfil y finalidad. No se admitirían dominios introducidos libremente, comodines de terceros ni cualquier URL por aparecer en una respuesta. Cada redirección se validaría. Una migración oficial a un host nuevo requeriría verificarlo antes de ampliar la lista.

Los endpoints del sitio no tienen un contrato público garantizado. Una redirección territorial legítima puede indicar que una fuente no está disponible o que necesita nueva validación; no justifica relajar las comprobaciones. El fallo debe ser visible, sin mezclar silenciosamente regiones.

Los requisitos publicitarios y de privacidad pueden diferir. Se conservarían los límites actuales de no inventar consentimiento ni reutilizar tokens, y se investigaría cualquier requisito adicional antes de declarar compatible la fuente. La documentación pública no incluiría HAR, cookies, tokens, URLs firmadas ni grabaciones sensibles.

## Pasos sugeridos para una implementación posterior

1. Identificar las fuentes regionales oficiales y registrar sus dominios y rutas públicas sin datos sensibles. Verificar LATAM desde una red con acceso legítimo. No dar por hecho que una región comercial equivale a un único país o idioma.
2. Comparar el catálogo y el detalle de episodios con el contrato actual. Registrar paginación completa, clips, bloqueos, audio, subtítulos y servicio. Si falta acceso legítimo, marcar la fuente como no verificada.
3. Decidir el alcance con esa evidencia. Si el contrato coincide, planificar perfiles y reutilización. Si necesita otro login, DRM o integración publicitaria, estimarlo por separado y mantener la fuente deshabilitada hasta resolver su compatibilidad.
4. Diseñar el cambio de fuente, la identidad compuesta y la migración del historial. Consultar `docs/design-system.md` antes de cualquier cambio de UI. Mantener Java Views, Media3/IMA y las garantías de AGENTS.md.
5. Implementar en otro cambio autorizado. Este PR solo contiene el plan. No necesita cambios de Java, XML, Gradle, player o streams.
6. Añadir pruebas con ejemplos regionales observados y expectativas independientes: URLs y redirecciones válidas e inválidas, paginación, clips, bloqueos, migración de historial, respuestas tardías y Next sin saltos de restricciones.
7. Ejecutar las comprobaciones de build, tests, lint y tokens indicadas en AGENTS.md. Para pruebas vivas usar únicamente `emulator-5554`, APK vigente y red oficial legítima. Comprobar cambio de región, reinicio, Continue, foco, Retry, audio real, subtítulos, sesión nueva, Next y autoplay. Verificar errores territoriales y de autenticación sin intentar eludirlos.
8. Registrar por fuente lo realmente comprobado. Probar cortes publicitarios servidos y DRM compatible antes de afirmarlos. El éxito en emulador no acredita TV física ni todos los episodios. No usar la TV física sin nueva autorización; habilitar solo las fuentes cuya compatibilidad se haya demostrado.
