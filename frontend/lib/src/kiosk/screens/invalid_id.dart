import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_i18n/flutter_i18n.dart';
import 'package:irmabalie/src/kiosk/screens/scan_help.dart';
import 'package:irmabalie/src/kiosk/screens/welcome.dart';
import 'package:irmabalie/src/kiosk/state/id_state.dart';
import 'package:irmabalie/src/theme/theme.dart';
import 'package:irmabalie/src/kiosk/widgets/kiosk_title.dart';
import 'package:irmabalie/src/util/platform_svg.dart';
import 'package:irmabalie/src/widgets/irma_button.dart';
import 'package:irmabalie/src/widgets/irma_outlined_button.dart';
import 'package:irmabalie/src/widgets/irma_themed_button.dart';
import 'package:provider/provider.dart';

// 1920, 1080

class InvalidId extends StatelessWidget {
  static const routeName = '/invalid_id';

  @override
  Widget build(BuildContext context) => Scaffold(
        body: Consumer<IdState>(
          builder: (context, idState, child) {
            return Column(
              children: <Widget>[
                KioskTitle(
                  text:
                      FlutterI18n.translate(context, 'kiosk.invalid_id.title'),
                ),
                Column(
                  children: <Widget>[
                    Padding(
                      padding: const EdgeInsets.all(78.0),
                      child: PlatformSvg.asset(
                        'assets/kiosk/warning.svg',
                        excludeFromSemantics: true,
                        width: 180,
                      ),
                    ),
                    Text(
                      FlutterI18n.translate(context, 'kiosk.invalid_id.body'),
                      style: IrmaTheme.of(context)
                          .kioskBodyHigh
                          .copyWith(fontWeight: FontWeight.bold),
                      textAlign: TextAlign.center,
                    ),
                    Padding(
                      padding: const EdgeInsets.only(top: 78.0),
                      child: Row(
                        children: <Widget>[
                          Spacer(flex: 1),
                          IrmaOutlinedButton(
                            minWidth: 550,
                            size: IrmaButtonSize.kioskBig,
                            label: 'kiosk.invalid_id.button_help',
                            textStyle:
                                IrmaTheme.of(context).kioskButtonTextDark,
                            onPressed: () {
                              Navigator.pushNamed(context, ScanHelp.routeName);
                            },
                          ),
                          Padding(
                            padding: const EdgeInsets.only(left: 60.0),
                            child: IrmaButton(
                              minWidth: 550,
                              size: IrmaButtonSize.kioskBig,
                              label: 'kiosk.invalid_id.button_close',
                              textStyle:
                                  IrmaTheme.of(context).kioskButtonTextNormal,
                              onPressed: () {
                                Navigator.popUntil(context,
                                    ModalRoute.withName(Welcome.routeName));
                              },
                            ),
                          ),
                          Spacer(flex: 1),
                        ],
                      ),
                    ),
                  ],
                )
              ],
            );
          },
        ),
      );
}
