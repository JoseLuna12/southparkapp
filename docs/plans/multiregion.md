# Multirregión con fuentes oficiales: plan Sol Medium

Historical proposal. Implemented behavior and current limits: [implementation guide](../multiregion.md).

Investigación de GPT-Sol, `gpt-6.1-sol`, reasoning medium. Revisado el 31 de octubre de 2026. Propuesta para una implementación posterior, sin cambios de aplicación.

## Conclusión y alcance

La misma app puede ofrecer una selección de fuente regional. Elegir "Latinoamérica · español latinoamericano" significaría consultar el catálogo y el detalle de episodios de una fuente oficial LATAM verificada, y pedirle una sesión oficial nueva. El idioma del contenido lo determina lo que entregue esa fuente. La selección no cambia el país real del usuario ni concede derechos de reproducción.

El selector es sencillo. El soporte completo tiene complejidad media si la segunda fuente conserva el JSON de catálogo y el flujo Android TV con HLS e IMA DAI actuales. Cambiar una constante no basta. Una fuente con otro login, DRM o sistema de anuncios necesita otra evaluación y puede quedar sin soporte. Hoy no hay evidencia suficiente para afirmar que LATAM encaje en el caso sencillo.

El primer alcance razonable sería la fuente actual y una segunda fuente LATAM comprobada, sin traducción de interfaz, sincronización de progreso entre regiones ni nuevos sistemas de autenticación. No se propone implementar ni modificar player o streams en este cambio.

## Evidencia utilizada y preguntas abiertas

Se revisaron [AGENTS.md](../../AGENTS.md), [README](../../README.md), [VALIDATION](../../VALIDATION.md), [protocolo del sitio](../site-protocol.md), el código enlazado abajo y el borrador de [PR #2](https://github.com/JoseLuna12/southparkapp/pull/2). Esta versión conserva sus límites de acceso y convierte sus supuestos en decisiones y criterios de aceptación.

Esta investigación es documental y de código. No se consultaron fuentes regionales en vivo, no se solicitaron sesiones ni se realizaron instalaciones o pruebas de reproducción. No se ha identificado ni validado aquí un endpoint LATAM. No se debe deducir su dominio, prefijo de idioma o estructura de rutas a partir del nombre comercial de la región.

Las observaciones previas de reproducción pertenecen a la red del emulador documentada en VALIDATION. No acreditan otras regiones. VALIDATION recoge una muestra de subtítulos ingleses, mientras el apartado de límites de site-protocol conserva una advertencia anterior más general. Ninguno acredita audio latinoamericano ni subtítulos LATAM. Tampoco hay validación previa de un corte publicitario servido o de DRM compatible.

Para decidir la viabilidad faltan cuatro respuestas concretas:

- ¿Cuál es la fuente oficial que publica episodios completos en español latinoamericano y desde qué territorios ofrece acceso legítimo?
- ¿Comparte el contrato de catálogo y detalle actual, incluyendo paginación y restricciones?
- ¿Entrega sesiones `androidtv` compatibles con HLS, `googledai` e `imasdk`, con sus requisitos de anuncios y consentimiento?
- ¿Qué audio y subtítulos entrega realmente cada episodio de muestra? Un título traducido no demuestra doblaje.

## Estado actual: fuente, región e idioma acoplados

No existe un modelo de región ni un registro de proveedores intercambiables. Hay un cliente concreto para una fuente oficial, instanciado tanto por el catálogo como por el reproductor.

| Punto del código | Supuesto actual | Consecuencia para multirregión |
| --- | --- | --- |
| [Catalog.java](../../app/src/main/java/com/unofficial/southparktv/Catalog.java), `BASE` y `officialPage` | Origen fijo `https://www.southparkstudios.com`, HTTPS, host exacto, sin usuario en URL y puerto predeterminado o 443 | Una ruta regional necesita resolverse y validarse contra su propio perfil. |
| `Catalog.seasons`, `episodes`, `seasonPage`, `pageData` | Datos `window.__DATA__`, rutas `/seasons/south-park` y `/episodes/`, campos y guías concretos | Un prefijo regional distinto puede descartar temporadas o marcar episodios como bloqueados aunque el JSON se parezca. |
| [SiteClient.java](../../app/src/main/java/com/unofficial/southparktv/SiteClient.java), `seasons` y `episodes` | Entrada `/seasons/south-park`; páginas `/api/context/.../episode/{página}/{tamaño}` | Hay que comprobar el contrato completo, no solo la primera página. Conserva deduplicación, límite de páginas y error ante repetición. |
| `SiteClient.download` | Solo hosts `www.southparkstudios.com`, `topaz.paramount.tech`, `images.paramount.tech`; valida cada redirección | Añadir un origen no autoriza cualquier destino que aparezca en su respuesta. Esta lista gobierna sus descargas de datos e imágenes, no todos los destinos internos de Media3 e IMA. |
| `SiteClient.playback` | Detalle actual del episodio; servicio HTTPS `topaz.paramount.tech/topaz/api/`; sesión nueva `androidtv`; HLS con `googledai` e `imasdk` | Otro contrato no funciona por cambiar `BASE`. El referer publicitario también se genera con la página oficial fija. |
| [WatchHistory.java](../../app/src/main/java/com/unofficial/southparktv/WatchHistory.java) | Posición por ID, una entrada global `lastEpisode`; decodificación con prefijos y origen actuales | Separar claves no basta: también hay que adaptar validación y restauración del historial. |
| [MainActivity.java](../../app/src/main/java/com/unofficial/southparktv/MainActivity.java) y [PlayerActivity.java](../../app/src/main/java/com/unofficial/southparktv/PlayerActivity.java) | Temporada global; lanzamiento con ID y rutas sin fuente; cada actividad crea `SiteClient` | La fuente debe acompañar navegación, restauración, Continue y Next para evitar resolver rutas en el origen incorrecto. |

El idioma de títulos y descripciones llega del catálogo. Audio y subtítulos dependen de las pistas del stream oficial. Los recursos Android definen el idioma de los botones. Son tres decisiones distintas: traducir la interfaz o cambiar el idioma de Android no obtiene doblaje.

La región efectiva la decide el proveedor con la red y sesión reales. `playback` detiene episodios `locked` o `authRequired`; no implementa login. Genera identificadores efímeros y parámetros de privacidad, conserva parámetros publicitarios concretos y descarta macros sin resolver. No modifica el país devuelto por el servicio ni reutiliza sesiones capturadas.

## Diseño propuesto

### Un perfil pequeño por fuente comprobada

La relación sería `selector → perfil oficial → catálogo y detalle → sesión nueva → pistas disponibles`.

Cada perfil tendría un ID estable independiente del texto visible, nombre regional, idioma esperado del contenido, origen HTTPS, ruta inicial y reglas exactas de hosts y rutas por finalidad. El protocolo compartido seguiría en el cliente existente cuando la evidencia confirme que coincide. Una diferencia observada justificaría una adaptación concreta; no hace falta un sistema de plugins ni un backend.

El idioma esperado, por ejemplo `es-419`, sería una preferencia, no una garantía ni una prueba de que la fuente usa esa etiqueta. Las reglas de URL distinguirían páginas, paginación, imágenes y servicio de reproducción. No incluirían tokens, manifiestos firmados, cookies ni permisos territoriales inventados.

El endpoint de sesión seguiría viniendo del detalle oficial actualizado del episodio y pasando sus gates. El perfil no construiría streams ni reemplazaría el servicio con uno de otra región. Si una fuente comparte dominio pero usa otro prefijo, las reglas de rutas también deben distinguirla. Autorizar el host por sí solo no establece coherencia regional.

### Qué significa elegir LATAM

La fuente actual seguiría siendo el valor inicial. LATAM se ofrecería como compatible después de verificar su origen, contrato y reproducción permitida. No habría un campo de URL libre ni detección de país que fuerce una selección.

La selección cambiaría temporadas, episodios, metadatos, enlaces externos oficiales y contexto de sesión. No conservaría el catálogo anterior bajo una etiqueta LATAM. El audio saldría de las pistas que entregue la fuente seleccionada. Si solo entrega audio original, el detalle debe comunicarlo sin prometer doblaje. Los subtítulos mantendrían selección nativa y preferencias de accesibilidad; no se generarían pistas ausentes.

La traducción de los controles Android sería otro alcance. Una fuente puede atender varios países e idiomas, y dos fuentes pueden ofrecer el mismo idioma. Por eso el identificador persistido debe representar la fuente, no solo el idioma ni un país supuesto.

### Cambio de fuente, estado e historial

Para reducir estados posibles, el selector estaría en el catálogo. Durante reproducción se saldría por el flujo normal que guarda posición y libera sesión antes de permitir cambiar fuente. No habría cambio de región dentro de un episodio en curso.

Al confirmar otra fuente, se invalidarían generaciones de catálogo y recomendaciones, se cancelarían solicitudes pendientes y se limpiarían listas y acciones del contexto anterior. Toda respuesta o imagen tardía tendría que comprobar la identidad de fuente además de los guards existentes. Cancelar un trabajo por sí solo no impide que una devolución ya encolada actualice la UI.

La nueva fuente cargaría sus temporadas y recuperaría su selección guardada si aún existe; si no, usaría una temporada publicada válida. Si falla la carga, conservaría visible la fuente elegida con Retry y opción de volver. No mostraría datos viejos como si pertenecieran a la nueva región ni haría fallback territorial silencioso. Foco y temporada seleccionada seguirían siendo estados distintos.

La identidad mínima de episodio sería fuente más ID oficial. Temporada seleccionada, posiciones, completado y última entrada se separarían por fuente. La migración asignaría los datos anteriores a la fuente actual, preservando progreso, y toleraría reinicios sin repetir o borrar la migración. Las rutas restauradas se validarían con ese perfil. No se copiarían posiciones entre regiones por título, número o coincidencia de ID: pueden existir cortes y duraciones distintos.

El perfil viajaría también en el lanzamiento y la restauración del reproductor; no se inferiría de una preferencia global que puede cambiar. Continue y Next operarían solo sobre el catálogo de esa identidad. Se conservarían Next inmediato y autoplay al final natural, sin saltar entradas bloqueadas ni buscar episodios en otra región. Una temporada de clips seguiría siendo un estado vacío válido, no una señal para buscar otra fuente.

## Decisión de compatibilidad y complejidad

La estimación es cualitativa, basada en los acoplamientos revisados. No es un compromiso de horas: falta inspeccionar la segunda fuente.

El selector y persistir su ID son de complejidad baja. El caso completo compatible es medio por la propagación del perfil, gates, historial, restauración, cancelación y regresiones. No sería un simple cambio de dominio, aunque no requiriese un nuevo motor de reproducción.

La decisión de reutilización tendría estos criterios:

- Si catálogo, detalle y sesión coinciden, reutilizar el flujo actual con configuración explícita y pruebas por fuente.
- Si solo cambian rutas o campos públicos, estimar una adaptación acotada de catálogo antes de habilitarla. No dar por compatible la reproducción por haber cargado tarjetas.
- Si exige otra autenticación, DRM o publicidad incompatible, mantener la fuente sin soporte y abrir una evaluación separada. El plan actual no autoriza esas implementaciones.
- Si no hay acceso legítimo para observarla, registrar "no verificada". Un bloqueo desde la red disponible no demuestra que el endpoint haya desaparecido ni permite certificarlo.

DRM puede exigir licencias, capacidades de dispositivo y políticas que el flujo actual no cubre. El emulador no acredita compatibilidad de una TV. No se propone otro esquema de descifrado. La autenticación también puede convertir una integración media en un proyecto aparte; mantener el rechazo de login es una primera versión válida, aunque limite el catálogo reproducible.

Los anuncios necesitan evaluación propia incluso con IMA DAI común. Pueden cambiar parámetros, consentimiento y comportamiento de cortes. Se conservaría el flujo oficial, sus restricciones de seek y sus reportes. Un episodio que arranca sin anuncios no valida la publicidad; parámetros de anuncios no personalizados tampoco equivalen a consentimiento válido para cualquier territorio.

Disponibilidad y subtítulos son variables por episodio. Un catálogo completo puede contener bloqueos, clips o episodios que luego rechaza el servicio. Deben comprobarse idioma audible, pistas seleccionables y cues reales; etiquetas, callbacks y títulos traducidos son evidencia insuficiente. La app no puede garantizar una pista que el proveedor no entrega.

## Riesgos y límites obligatorios

La preferencia regional no concede acceso territorial. El proveedor conserva la decisión por ubicación real, derechos, cuenta y dispositivo. Ante un rechazo se mostraría un error y se permitiría volver o abrir la página oficial opcionalmente. No se probarían otras regiones para lograr reproducción.

Quedan excluidos VPN, proxy, falsificación de país o cabeceras de ubicación, credenciales capturadas, eliminación de anuncios, fuentes alternativas y bypass de DRM o autenticación. Tampoco se reutilizarían tokens de la fuente anterior.

Cada salto de redirección seguiría validándose. Un redireccionamiento legítimo a otro territorio se trataría como incompatibilidad o cambio pendiente de verificar, no como autorización para mezclar perfiles o ampliar hosts automáticamente. No se aceptarían comodines de terceros ni cualquier URL porque aparezca en JSON oficial.

Los endpoints son interfaces de un sitio, no una API pública garantizada. Sus cambios pueden invalidar una fuente sin afectar a otra. Los errores deberían distinguir, cuando la respuesta lo permita, acceso denegado, contrato cambiado y fallo de red; un HTTP genérico no basta para afirmar que es geobloqueo. HAR, cookies, tokens, URLs firmadas y grabaciones sensibles no formarían parte del documento ni de fixtures publicados.

## Pasos sugeridos y pruebas de aceptación futuras

1. Identificar una fuente LATAM oficial mediante referencias públicas oficiales. Registrar origen, rutas y territorios declarados sin datos sensibles. Desde una red con acceso legítimo, comparar catálogo, detalle, sesión, publicidad y pistas. Si falta esa evidencia, detener la habilitación de LATAM.
2. Documentar el resultado por fuente: observado, incompatible o no verificado. La puerta de implementación sería demostrar catálogo paginado completo y una sesión permitida compatible, o delimitar explícitamente la adaptación necesaria. Separar compatibilidad técnica de disponibilidad de derechos.
3. Diseñar el perfil y la migración de estado con las reglas anteriores. Revisar `docs/design-system.md` antes de diseñar el selector. Conservar Java Views, componentes existentes y Media3/IMA; cualquier dependencia nueva necesitaría necesidad demostrada y aprobación.
4. Implementar únicamente en un cambio futuro autorizado. Mantener el alcance inicial de dos fuentes y rechazo de mecanismos no compatibles. Revisar todos los puntos de la tabla, incluidos referer, fallback oficial, decodificación del historial y restauración de actividades.
5. Añadir pruebas con expectativas independientes y ejemplos sanitizados observados. No copiar el algoritmo de producción para calcular resultados esperados. Cubrir al menos los casos siguientes.

| Caso | Resultado exigido |
| --- | --- |
| URL de otro perfil, host parecido, usuario en URL, puerto ajeno, downgrade HTTP o redirección no permitida | Rechazo sin solicitud al destino prohibido. |
| Temporada con más de una página, página repetida y temporada solo de clips | Todas las entradas en orden, error explícito ante repetición y vacío legítimo para clips. |
| Cambio A → B con catálogo, recomendación e imagen de A retrasados | Ningún dato ni acción de A aparece como B; Retry conserva B. |
| Mismo ID en A y B; reinicio tras migrar historial antiguo | Progreso independiente; datos antiguos intactos bajo A y migración repetible sin pérdida. |
| Continue, restauración de actividad y Next en B | Rutas y sesión de B; nueva sesión por inicio; guardado y liberación antes de cambiar identidad. |
| Siguiente entrada bloqueada, autenticación requerida o rechazo territorial | No saltar ni cambiar de fuente, no crear una vía alternativa de reproducción. |
| Preferencia de español sin pista correspondiente | No afirmar doblaje ni subtítulos disponibles; mantener opciones reales del proveedor. |

6. Ejecutar build, tests, lint y tokens con los comandos de AGENTS.md. Para pruebas vivas usar solo `emulator-5554`, APK vigente y red con acceso oficial permitido. Verificar fuente actual y segunda fuente, cambio de selección, reinicio, foco, Retry, Continue, Fit, controles remotos, ambos Back, Next y autoplay. Medir audio real, además de callbacks, y observar subtítulos reales.
7. Registrar qué se probó por fuente y episodio, incluida la red legítima y los límites de la muestra sin datos sensibles. Validar cortes servidos antes de afirmar anuncios, y compatibilidad de licencias y dispositivo antes de afirmar DRM. No usar la TV física sin nueva autorización. Si falta evidencia, reducir la promesa de soporte en vez de relajar restricciones.

## Verificación de este documento

Se contrastaron los supuestos con código y documentación locales y con el borrador del PR #2. Los enlaces relativos y el diff se comprueban antes de publicar. No se ejecutan build ni pruebas de emulador en este cambio documental. La viabilidad regional en vivo queda pendiente de la primera fase propuesta.
