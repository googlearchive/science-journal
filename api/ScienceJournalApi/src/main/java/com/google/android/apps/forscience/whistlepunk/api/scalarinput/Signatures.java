/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import java.util.Arrays;
import java.util.HashSet;

public class Signatures {
  /**
   * The signature string derived from the public signature of Science Journal installed from the
   * Play Store.
   *
   * <p>This string stays constant between versions of Science Journal; it is the signature of our
   * public signing certificate.
   */
  public static final String SCIENCE_JOURNAL_RELEASE =
      "308203c3308202aba003020102020900977a0181867e41eb300d06092a8648"
          + "86f70d01010505003078310b3009060355040613025553311330"
          + "1106035504080c0a43616c69666f726e69613116301406035504"
          + "070c0d4d6f756e7461696e205669657731143012060355040a0c"
          + "0b476f6f676c6520496e632e3110300e060355040b0c07416e64"
          + "726f69643114301206035504030c0b77686973746c6570756e6b"
          + "301e170d3135303830333137353132305a170d34323132313931"
          + "37353132305a3078310b30090603550406130255533113301106"
          + "035504080c0a43616c69666f726e69613116301406035504070c"
          + "0d4d6f756e7461696e205669657731143012060355040a0c0b47"
          + "6f6f676c6520496e632e3110300e060355040b0c07416e64726f"
          + "69643114301206035504030c0b77686973746c6570756e6b3082"
          + "0122300d06092a864886f70d01010105000382010f003082010a"
          + "0282010100f362863d431cc7eee8bac1b2f7ccde72475da4e03f"
          + "f5ba83fb64bed8ac055f5c6ac32a33a2ede1f29b42d43b0b1c57"
          + "e75a986f4273754e38937ce884b09cd28b0ebd815fedce9f2b81"
          + "851b009f6fcb844497d5cbd5ef36d5a2aa2c766f1ac6f00ee370"
          + "1c339d59cad05ff4a34f19b09929d4aa5ef0df08ef2af5e2b1b3"
          + "3b309599aeef711434861fd59bef0a7d10340c13006bc482b55a"
          + "e7e8763806cc7fbf1f6dc2cc6750c549cde49597f2507f5785a9"
          + "bf4cc69582be228b6c4fee57e445b8ca41b18055bcee6b09cda3"
          + "5311572518b578cca00e800aa693b2a6714e4d36e7ad6dea3482"
          + "fedeafc988ebf57b0cb861cf13eb8a880a53bc3f3d71f5c833ca"
          + "b10203010001a350304e301d0603551d0e041604146f035e238a"
          + "3b6c3624227fb20ac4e58af3c1f508301f0603551d2304183016"
          + "80146f035e238a3b6c3624227fb20ac4e58af3c1f508300c0603"
          + "551d13040530030101ff300d06092a864886f70d010105050003"
          + "82010100be08a60ab0eec9f341b67d01d661a163e13283d1ba1e"
          + "f845a5530f1afeae5472c1c7ddd1d791b0229ba2d743eb19a556"
          + "3a7f0be9932d64feef7b898cb6cd06a88e38dd07de92bc2209ee"
          + "14497c829def6c03f369de9eda868c8c4f9832aeebe7ae703f10"
          + "95aca4190369d440432fbb82df1f87fb3f546a75d5d9b78d5e08"
          + "4df071e31b3b903c518be481896fe6c8e8dde0c7c249f84058d1"
          + "7a75fd901b3c9ab37df17753127fb380a97101fbd7dd40c70a44"
          + "945eff53034aeeccfe298dd07e8ad0d6c2409f62876628e2a37c"
          + "653ab6bfc1d8f9f863f189e7d8ab2c552ddacd82b550a85e5ae2"
          + "aac37590449a43816987dbbedf238ff91b8ff53b7df8257f462b";

  /**
   * The signature used internally by the Science Journal team at Google. Not likely useful to
   * third-party developers, but harmless.
   */
  public static final String SCIENCE_JOURNAL_GOOGLE_INTERNAL_DEBUG =
      "308203c3308202aba003020102020900f1fff3f0b50b3262300d06092a8648"
          + "86f70d01010505003078310b3009060355040613025553311330"
          + "1106035504080c0a43616c69666f726e69613116301406035504"
          + "070c0d4d6f756e7461696e205669657731143012060355040a0c"
          + "0b476f6f676c6520496e632e3110300e060355040b0c07416e64"
          + "726f69643114301206035504030c0b77686973746c6570756e6b"
          + "301e170d3135303830333137353131345a170d34323132313931"
          + "37353131345a3078310b30090603550406130255533113301106"
          + "035504080c0a43616c69666f726e69613116301406035504070c"
          + "0d4d6f756e7461696e205669657731143012060355040a0c0b47"
          + "6f6f676c6520496e632e3110300e060355040b0c07416e64726f"
          + "69643114301206035504030c0b77686973746c6570756e6b3082"
          + "0122300d06092a864886f70d01010105000382010f003082010a"
          + "0282010100e7172dc2f94de65cd50a2f6e46114f2029c740f3b3"
          + "3348e45686f7d5028c8b2ff6b8f2d5b6c86aad822381b94fa4c0"
          + "6874ade505c31231c3586b736d112f53b2a82b65f0f12f481810"
          + "38ab1817047f3b882bc7990cbf71cb718c05312642a1824830cf"
          + "9d01bd3e192801b156bc70f15ee5b0999512f02b082d2ff9d809"
          + "91dcbb48a573d0f765ec1648f7efd04b2b9d393a56f781067384"
          + "2e46a3483cbb6cfa307891066ded65c420e8efebe663feca200f"
          + "4bdf0cbff27f58e816ca371fb080410c70da413a4338f7358b7f"
          + "8e230a778c38d50f66826a9a803540d6fc9884d401bca75d24f0"
          + "0836e8e4105fccf4842bb0a36f1be245272e35298e3c233838cb"
          + "7f0203010001a350304e301d0603551d0e041604148a8de07ea9"
          + "46145622275ec7767d344ed510600a301f0603551d2304183016"
          + "80148a8de07ea946145622275ec7767d344ed510600a300c0603"
          + "551d13040530030101ff300d06092a864886f70d010105050003"
          + "820101005646831c12b0bdd2bb54154f0ae81bef0b8d5663c057"
          + "2e5b7627605a5efa19eca354f0467b54e7e1e7eb5dbc4e026140"
          + "978758ca626d884eedfae2cf606777fd7de45784aa4196c11634"
          + "4f167519b5c989949de0552e762bc2ab11e0d4a2c9769318a879"
          + "407ced16742ae1b9bf768366bbac35ae9529c9adaa5342eba0a4"
          + "9ff878c8a7d6b12dd10d032ca6caa1ebfcef29f45b4c49ef1d14"
          + "25a68d822768dc2449a793fb12cb251c2be715d3a843d8bebde8"
          + "2b592ab839afd748f46e3636060d4fce77bc9212c6349d763d70"
          + "99db59a2a3149225654aeadc077152cf455aad5778135c412aeb"
          + "bfaaa10b3323ee5794b559bc6c8efeb14646398da5ded4db3f60";

  public static final HashSet<String> DEFAULT_ALLOWED_SIGNATURES =
      new HashSet<>(Arrays.asList(SCIENCE_JOURNAL_RELEASE, SCIENCE_JOURNAL_GOOGLE_INTERNAL_DEBUG));
}
