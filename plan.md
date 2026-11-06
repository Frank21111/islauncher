# Plan: bloqueos de apps y sitios web por horario (sin Device Owner)

Estado: **implementado (fases 1 a 4), integrado a `main` desde `feat/blocks`**. Este documento conserva el diseño y los resultados originales.

Actualización del 2026-11-06: la activación ahora permite habilitar los tres accesos desde Android sin ADB. El permiso de ajustes seguros queda como protección opcional. Ver `docs/testing/blocks-without-adb.md` para las pruebas de esta modalidad.

## Objetivo

Definir reglas con apps, sitios y rangos horarios. Mientras un rango de la regla está en curso, no se puede debilitar
desde el teléfono, y esquivarla tiene que ser lo más difícil posible **sin quitar ninguna cuenta**.

## Conclusiones que condicionan el diseño

1. **Sin quitar cuentas no hay Device Owner, y la garantía baja.** Device Owner hacía que el sistema aplicara el
   bloqueo. Acá lo aplica nuestra app mientras esté corriendo. Queda difícil de esquivar, pero no imposible.
2. **El mecanismo es un servicio de accesibilidad.** Detecta qué app pasa a primer plano, venga de donde venga
   (launcher, recientes, notificación u otra app), y manda al inicio. Es el enfoque habitual de los bloqueadores.
3. **Un único paso por adb refuerza lo que el usuario podría apagar, sin tocar cuentas:**
   `pm grant WRITE_SECURE_SETTINGS` permite que la app se vuelva a habilitar sola y fije ajustes del sistema.
   `dpm set-active-admin` impide desinstalarla directamente.
4. **El bloqueo de sitios funciona con cualquier navegador, de una de dos formas.** Los navegadores se detectan por lo
   que declaran (abrir cualquier enlace `https`), no con una lista. Si se puede leer la URL, se filtra por sitio. Si no,
   ese navegador se bloquea entero durante el rango. Android no da una forma común de leer la URL, así que la lectura es
   heurística y puede dejar de funcionar con una actualización; en ese caso el navegador queda bloqueado, no abierto.
5. **La accesibilidad ve el contenido de la pantalla, y el acceso a notificaciones ve todas las notificaciones.** Sin
   `INTERNET` no puede salir del teléfono. Aun así, ninguno de los dos servicios guarda ni registra lo que lee, y el
   README lo tiene que declarar.
6. **Notificaciones y audio se resuelven con un servicio de escucha de notificaciones**, que además da acceso a las
   sesiones de medios de todas las apps. Se pueden ocultar las notificaciones y pausar el audio. No encontré una API
   pública que impida el sonido de una notificación antes de que se publique, ni que diga qué app está reproduciendo
   audio si no tiene sesión de medios.
7. **adb queda fuera de los bloqueos.** Sigue activo durante los rangos, `scripts/install.sh` funciona siempre y adb
   queda como vía para esquivarlos.

Descartado: congelar las apps con cuentas para engañar a `dpm`. No es oficial y, hasta donde recuerdo del código de
Android (sin verificar), el sistema puede borrar las cuentas cuyo autenticador desaparece. Sería perderlas igual.

Descartado: filtrar por red con un VPN en una segunda app con `INTERNET`. Sería lo más independiente del navegador y
cubriría WebView, pero rompe el criterio de no agregar funciones que necesiten red, esa app vería todos los dominios, y
el DNS seguro de los navegadores lo puede esquivar.

## Fase 0: prueba en el teléfono (puerta de decisión)

Se hizo el 2026-11-05 en un moto g42 con Android 13 (parche de junio de 2025), con 5 cuentas y sin perfil de trabajo.
No se usó una rama: el probe fue una app aparte (`io.github.gastoncampana.launcher.probe`, sin `INTERNET`), compilada
fuera del repo e instalada por adb. Al terminar se desinstaló, y los ajustes del teléfono quedaron idénticos a los
registrados antes de empezar.

**Resultados:**

- [x] **Permisos por adb:** `pm grant … WRITE_SECURE_SETTINGS` y `dpm set-active-admin` funcionan con las 5 cuentas
      presentes y sin el paso de ajustes restringidos.
- [x] **Forzar la detención:** Android quita el servicio de accesibilidad de la lista. En menos de 1 s vuelve a conectar
      el servicio de notificaciones, el proceso arranca, reescribe el ajuste en 1 ms y la accesibilidad se reconecta. No
      hace falta tocar Home. Con el administrador activo, Ajustes deshabilita el botón "Forzar detención".
- [x] **Apagar los servicios:** si se apaga la accesibilidad, se reactiva en 1 ms. El acceso a notificaciones **no** se
      puede restaurar desde la app: el sistema ignora `enabled_notification_listeners`. Hay que impedir llegar a esa
      pantalla durante un rango.
- [x] **Desinstalar:** con el administrador activo, `adb uninstall` falla con `DELETE_FAILED_DEVICE_POLICY_MANAGER`. Desde
      Ajustes, "Desinstalar" lleva a la pantalla para desactivar el administrador.
- [x] **Pantallas a proteger,** identificables por clase y título de ventana:
      - Info de la app: `InstalledAppDetailsTop`, con el nombre de la app en `entity_header_title`.
      - Desactivar el administrador: `deviceadmin.DeviceAdminAdd`, con `admin_name`.
      - Página propia del servicio: `SubSettings`, con el título igual al nombre del servicio.
      - Acceso a notificaciones: `NotificationAccessSettingsActivity`.
      - Accesibilidad: `AccessibilitySettingsActivity`.
      - Fecha y hora: `DateTimeSettingsActivity`.
      - Opciones para desarrolladores: `DevelopmentSettingsDashboardActivity`.
- [x] **Leer la URL en los 7 navegadores** (Chrome, Brave, Edge, Opera, DuckDuckGo, Samsung Internet, Firefox) con el
      servicio real, en pestaña normal, incógnito o privada, Custom Tab, con la barra oculta y escribiendo en la barra.
      Tarda entre 0,1 y 1,6 s. Incógnito y modo secreto bloquean las capturas, pero no el árbol.
- [x] **Barra oculta:** Edge, Opera, DuckDuckGo y Samsung la ocultan al desplazar, y su nodo desaparece. Al tocar un
      enlace a otro sitio, la barra vuelve a aparecer y el nuevo dominio se lee en menos de 1 s, en los 4.
- [x] **Salir de un sitio bloqueado** con "atrás": vuelve a la página anterior en 0,15 a 0,5 s, o a la app de origen si
      la pestaña se abrió desde otra app. Al reabrir el navegador no queda atrapado en la página bloqueada.
- [x] **App bloqueada:** vuelve al inicio a los 0,14 a 0,7 s, al abrirla desde el lanzador o desde Recientes.
- [x] **Notificaciones:** se posponen a los 220 ms de publicarse. El sonido arranca a los ~500 ms y se corta a los 5 ms
      (sin bloqueo dura 2,6 s), así que en la práctica no suena. Al terminar el bloqueo reaparecen y suenan una vez cada
      una. Las notificaciones persistentes de medios (`fgs ongoing media`) también se pueden posponer.
- [x] **Audio con sesión de medios:** se pausa, y si se reanuda con la tecla multimedia vuelve a pausarse en menos de
      0,5 s.
- [ ] **No probado:**
      - Llamadas de una app bloqueada: hace falta un segundo teléfono.
      - Audio de apps sin sesión de medios.
      - Apps web instaladas (WebAPK, TWA).
      - Pantalla dividida y PiP.
      - Escritura de `auto_time` desde la app.
      - Android 14 o más nuevo: este teléfono tiene Android 13.

**Cambios al diseño que salen de la prueba:**

- **Lectura de la URL:** sin ids propios de cada navegador. Se usan nodos cuyo id contenga `url`, `omnibar`,
  `location_bar`, `address` o `domain`, y que estén en la franja superior (15 %) o inferior (20 %) de la pantalla. Se
  buscan en su texto **y** en su descripción, porque Firefox pone la URL en la descripción. Hay que quitar los caracteres
  de control bidi, porque Samsung antepone U+200E. Así se descartan los falsos positivos medidos: sugerencias mientras
  se escribe, accesos directos de la página de inicio ("Booking.com" en Opera, "recent.tab.url" en Firefox) y dominios
  que aparecen dentro de la página.
- **"Escribiendo":** solo cuenta un campo con foco **de la barra**, nunca uno de la página. Con un campo de la página
  enfocado (un login de HN), el sitio dejaba de bloquearse.
- **Último dominio:** se conserva el último leído mientras la barra está oculta, porque la barra reaparece al cambiar de
  sitio.
- **Raíz de la página:** los motores web no exponen la URL ahí (Chromium solo da `chromeRole` y otros datos), así que
  los navegadores internos de otras apps siguen sin filtrarse.
- **Ventanas emergentes:** hay que leer todas las ventanas (`getWindows`), no solo la activa, porque un menú o una
  promoción tapa la barra.
- **Componentes:** el ajuste de servicios habilitados se compara por componente, porque puede tener la forma corta
  (`pkg/.Cls`) o la larga.
- **Reactivación:** escribir el ajuste vuelve a disparar el observador, así que hay que evitar el bucle. En el probe
  pasó con el acceso a notificaciones y se cortó a los 13 s.
- **Inicio de un rango:** hay que revisar las sesiones de audio y las notificaciones activas, no solo esperar eventos.

**Criterio:** ningún navegador puede mostrar un sitio bloqueado sin que se detecte; el que no se pueda leer se bloquea
entero. Si eso deja sin filtro por sitio a los navegadores que usás, se vuelve a decidir.
Si el servicio no se recupera tras forzar la detención, o esas pantallas no se pueden proteger, hay que replantear si
el resultado alcanza. Las apps pueden avanzar sin los sitios.

## Fase 1: modelo puro y tests

Nuevo `Blocks.kt`, sin dependencias de Android:

- `Block(id, name, packages, sites, windows)` y `Window(days, startMinute, endMinute)`.
- `activeAt(now)` y `nextChange(now)`, con rangos que cruzan medianoche y cambios de horario.
- `urlFromTree(nodes)`: heurística que busca la URL entre los textos de la ventana (campo no enfocado, forma de dirección,
  posición), sin ids propios de cada navegador. Se prueba con árboles capturados en la fase 0.
- `siteMatches(url, sites)`: dominio y subdominios, sin confundirlo con texto que se está escribiendo.
- `tightened(old, new)`: en una regla activa solo se acepta lo que agrega apps, sitios o rangos.
- **Validación contra el bloqueo eterno:** se rechaza una regla sin al menos 1 hora libre seguida por semana.
- `trustedNow`: ancla la hora con `elapsedRealtime`, de modo que cambiar el reloj no termine un rango.
- Persistencia en las prefs `blocks`, fuera de `Layout`: restaurar un backup o importar de Nova no puede tocar las
  reglas.

## Fase 2: aplicación de las reglas

**`BlockService` (AccessibilityService):**

- Con cada cambio de ventana compara el paquete con las reglas activas. Si está bloqueado, vuelve al inicio y avisa
  "Bloqueada hasta HH:MM".
- Revisa la app en primer plano al llegar a `nextChange` y al encender la pantalla, para cortar lo que ya estaba abierto
  cuando empieza un rango.
- **Sitios:** detecta los navegadores por sus filtros de intent, incluidos los recién instalados. Si la URL coincide con
  una regla, sale de la página. Si al abrir el navegador no se puede leer la URL, lo manda al inicio. Con la barra oculta
  por desplazamiento mantiene la última URL leída en esa ventana.
- **Llamadas de una app bloqueada:** se bloquean igual, porque forman parte de la app: la pantalla de llamada vuelve al
  inicio y la notificación se pospone.
- **Protección durante un rango:** sale de las pantallas de Ajustes que permiten desactivar el bloqueo, las identificadas
  en la fase 0, además de Protección avanzada. Es por mejor esfuerzo y varía según fabricante.
- **Nunca bloquea:** Islauncher, teléfono, SMS predeterminado ni el resto de Ajustes.
- No guarda ni registra URLs, textos ni apps abiertas.

**`BlockListener` (NotificationListenerService), notificaciones:**

- Pospone con `snoozeNotification` las notificaciones de apps bloqueadas hasta el final del bloqueo: las nuevas al
  llegar y las que ya estaban al empezar el rango. Así no se pierden, y reaparecen al terminar.
- Solo usa el paquete y la clave de cada notificación. No lee ni guarda el contenido.

**`BlockListener`, audio:**

- Con `getActiveSessions` encuentra las sesiones de medios de apps bloqueadas y las pausa. Si vuelven a reproducir, las
  pausa de nuevo. Al terminar el rango no reanuda nada.
- **Apps sin sesión de medios:** al mandar al inicio una app bloqueada, si sigue sonando algo y no hay una app permitida
  reproduciendo, el launcher pide y suelta el foco de audio. Android solo aplica el silencio a apps de medios o juegos
  que pidieron el foco. Desde Android 15, además, solo se puede pedir estando en primer plano o con un servicio en
  primer plano, así que funciona en ese momento y no siempre.
- **Navegadores:** al empezar un rango con sitios se pausa su audio una vez, porque la sesión no indica qué sitio suena.

**Refuerzo durante un rango, en el launcher y los servicios:**

| Mecanismo | Qué cierra |
|---|---|
| Observar `enabled_accessibility_services` y `enabled_notification_listeners`, y reescribirlos conservando los demás | Apagar los servicios |
| Volver a habilitarlos al iniciar el launcher | Forzar la detención, al volver al inicio |
| Administrador de dispositivo activo | Desinstalar directamente |
| `auto_time` y `auto_time_zone` en 1, más `trustedNow` | Cambiar hora o zona |

- Sin el paso por adb no se pueden crear reglas: el servicio se apagaría en dos toques y no cumple lo pedido.
- **Manifest:** servicios con `BIND_ACCESSIBILITY_SERVICE` y `BIND_NOTIFICATION_LISTENER_SERVICE`,
  `WRITE_SECURE_SETTINGS`, receiver de admin sin políticas.
  `INTERNET` sigue eliminado, y se verifica con `apkanalyzer`. No se declara `isAccessibilityTool`: esto no es una
  herramienta de accesibilidad.

## Fase 3: integración y UI

- `pauseIfPoison`, el punto por el que pasan todas las aperturas, corta antes si la app está bloqueada: avisa, sin pausa
  y sin `countUse`. Sigue funcionando aunque el servicio esté apagado.
- Nueva `Section.BLOCKS` en Ajustes:
  - Muestra el estado de cada requisito y el comando de activación.
  - Lista de reglas y editor (apps, sitios, días y horas).
  - Una regla activa muestra el candado y la hora de liberación, y solo admite agregar.
  - Una regla con sitios lista los navegadores detectados y si cada uno se filtra por sitio o se bloquea entero.
  - Explica qué ven la accesibilidad y el acceso a notificaciones antes de activarlos.
- Quitar el administrador y el permiso solo se permite sin rangos activos.
- La opción es voluntaria: quien no la active no ve ningún otro cambio, y sin los servicios no se lee nada de la pantalla
  ni de las notificaciones.
- Nuevo `scripts/blocks-setup.sh`: otorga los permisos y comprueba el resultado.
- Textos en `values` y `values-es`, y entrada en la Guía en ambos idiomas.

## Fase 4: pruebas de bypass y documentación

Se hizo el 2026-11-05 en el mismo moto g42, con Islauncher instalado por `scripts/install.sh` (el launcher
predeterminado siguió siendo el de Motorola) y activado con `scripts/blocks-setup.sh`, sin quitar cuentas. Bloqueo de
prueba creado desde el editor: Opera, iana.org, lunes a viernes de 9:00 a 17:40, en curso.

**Resultados:**

- [x] **Abrir la app bloqueada:** desde el ícono, Recientes y el cajón de Islauncher, no abre (el cajón muestra
      "Bloqueada hasta 17:40" y no la abre; las otras vías vuelven al inicio en menos de 1 s, con el aviso).
- [x] **El sitio bloqueado:** abierto desde otra app, en Chrome y en Firefox, vuelve atrás y cierra esa pestaña; desde un
      enlace en example.com, vuelve a example.com. Incógnito y los 7 navegadores se midieron en la fase 0.
- [x] **Debilitar el bloqueo en curso:** quitar Opera solo muestra "No se puede quitar mientras el bloqueo esté en curso";
      "Quitar bloqueo" queda deshabilitado con su hora; agregar Chrome sí se guarda.
- [x] **Apagar el servicio por el ajuste:** vuelve en menos de 1 s.
- [x] **Forzar la detención:** el servicio sigue o vuelve en menos de 1 s; Ajustes deshabilita el botón.
- [x] **Desinstalar:** `adb uninstall` falla con `DELETE_FAILED_DEVICE_POLICY_MANAGER`.
- [x] **Pantallas de Ajustes:** la info de Islauncher, su página de accesibilidad, acceso a notificaciones y fecha y hora
      vuelven al inicio; la lista general de accesibilidad no.
- [x] **Hora y zona automáticas:** apagadas por adb, vuelven a encenderse solas (la zona tardaba hasta la siguiente
      revisión: ahora se reintenta al segundo).
- [x] **Audio:** al agregar Chrome al bloqueo en curso, su música se pausó al guardar (estado 3 → 2) y su notificación de
      reproducción, persistente, quedó pospuesta. Reanudar con la tecla multimedia vuelve a pausarse en menos de 2,4 s.
- [x] **Actualizar la app durante el bloqueo:** el servicio, el administrador y el acceso siguen; vuelve a bloquear a los
      pocos segundos de terminar la instalación.
- [x] **Navegador con la barra oculta en una página permitida:** no se bloquea; volver a abrirlo tampoco.
- [ ] **Sin probar:** el reinicio del teléfono durante un rango, una app reinstalada o clonada, el perfil de trabajo,
      cambiar de launcher, una notificación nueva de una app bloqueada (la fase 0 midió la API con el mismo código),
      auriculares o Bluetooth, y llamadas.

**Errores que encontró la fase 4, corregidos:**

- **Tecla multimedia:** la música volvía a sonar porque el servicio no retenía el controlador de la sesión y Android lo
  liberaba con su aviso. Ahora lo retiene.
- **Zona horaria:** quedaba manual porque el límite de una escritura por segundo descartaba el segundo cambio. Ahora se
  reintenta.
- **Avisos invisibles:** los toasts del servicio no se veían. Android 13 suprime los de una app en segundo plano sin
  notificaciones, así que ahora es una capa de accesibilidad, que no necesita permiso.
- **Caracteres bidi:** había caracteres invisibles reales en el código fuente (los marcó el lint); ahora son escapes.
- **Launcher predeterminado:** se suma a las apps que nunca se bloquean, porque es adonde vuelve una app bloqueada.

**Hallazgo de la herramienta de prueba:** `uiautomator dump` suspende los servicios de accesibilidad mientras corre. Al
volver, el servicio arranca sin lo leído y un navegador con la barra oculta vuelve al inicio a los 2,5 s. En uso real eso
solo pasa cuando el servicio se reinicia (actualización, detención forzada), y falla del lado seguro.

Documentación: `CLAUDE.md` y `README.md` actualizados con los permisos nuevos y lo que pueden ver ambos servicios.

## Límites conocidos

Lo que este plan no cierra, y que Device Owner cerraba en parte:

- **La app bloqueada se ve un instante** antes de volver al inicio.
- **adb:** queda activo durante los rangos. Desde una PC, o con depuración inalámbrica, se pueden quitar los permisos o
  desinstalar la app.
- **Sonido de notificación:** el reproductor llega a arrancar unos milisegundos antes de cortarse (5 ms medidos).
- **Audio sin sesión de medios:** si la app tampoco respeta el foco de audio, sigue sonando y no se puede identificar.
- **Audio de un sitio bloqueado en el navegador:** se puede reanudar desde el control de medios mientras el navegador
  esté en segundo plano, porque no se ve qué sitio suena.
- **La app sigue corriendo en segundo plano:** no se la puede detener, aunque no se vea ni se oiga.
- **Modo seguro:** apaga el servicio. También apaga las apps de terceros, pero no filtra un navegador preinstalado.
- **Un navegador abierto cuando empieza un bloqueo con sitios,** con la barra oculta, vuelve al inicio una vez: el
  servicio solo lee la barra durante un bloqueo, y no sabe qué página tenía.
- **Pantallas de Ajustes:** si un fabricante usa otras o una actualización las cambia, la protección puede fallar sin aviso.
- **Protección avanzada de Android 17:** revoca el servicio a apps que no son herramientas de accesibilidad. Activarla es
  un bypass, y con ella activa esta función no puede funcionar. No hay un aviso propio: Ajustes → Bloqueos muestra el
  servicio de accesibilidad como faltante (detectarla con su API pediría un permiso más, y en Android 13 no se pudo probar).
- **Sitios:** los navegadores internos de otras apps (WebView) no se filtran, porque esas apps no se detectan como
  navegador. Para cortarlos hay que bloquear esa app.
- **Lectura de la URL:** si un navegador cambia su interfaz, puede pasar a bloquearse entero, y no se puede descartar una
  lectura equivocada. La página bloqueada puede verse un instante antes de salir.
- **Llamadas de una app bloqueada:** no se pueden atender durante el rango, y el timbre podría seguir sonando. Sin
  probar: hace falta un segundo teléfono.
- **Recovery:** restablecer de fábrica sigue siendo una salida.
- **Fuera de un rango activo** la regla se puede borrar, incluso un minuto antes de que empiece. Es lo pedido.

## Decisiones tomadas

- Plan aprobado: esta garantía y los dos servicios que ven la pantalla y las notificaciones.

- No se quitan cuentas: sin Device Owner.
- adb queda fuera de los bloqueos.
- El bloqueo de sitios funciona con cualquier navegador: se filtra por sitio o se bloquea entero.
- Las llamadas de una app bloqueada también se bloquean.

## Fuentes

- [Key Mapper: permisos por adb](https://docs.keymapper.club/user-guide/adb-permissions/)
- [Leer la URL de Chrome con accesibilidad](https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/38783205/android-read-google-chrome-url-using-accessibility-service)
- [Chrome Accessibility on Android](https://chromium.googlesource.com/chromium/src/+/a3dafb8/docs/accessibility/browser/android.md)
- [Android 17: Protección avanzada y accesibilidad](https://thehackernews.com/2026/03/android-17-blocks-non-accessibility.html)
- [Android Authority: prueba en Canary](https://www.androidauthority.com/android-advanced-protection-mode-accessibility-apk-teardown-3640742/)
- [HyperOS: forzar detención y accesibilidad](https://awesome.ecosyste.ms/projects/github.com%2Fchickendrop89%2Fhyperos-accessibility-fix)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager)
- [AudioPlaybackConfiguration](https://developer.android.com/reference/android/media/AudioPlaybackConfiguration)
- [Foco de audio](https://developer.android.com/media/optimize/audio-focus)

A confirmar en la fase 0, porque no los verifiqué en la documentación: `dpm set-active-admin` con cuentas presentes, que
el administrador impida desinstalar, que escribir los ajustes evite el paso de ajustes restringidos, y los límites de
`snoozeNotification` (la documentación que pude leer solo da una línea).
