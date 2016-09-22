<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:template match="/">
        <html>
            <body>
                <table>
                    <tr>
                        <th>Name</th>
                        <th>Version</th>
                        <th>Patch Update</th>
                        <th>Minor Update</th>
                        <th>Major Update</th>
                    </tr>

                    <xsl:for-each select="libraries/lib">
                        <tr>
                            <td><xsl:value-of select="name"/></td>
                            <td><xsl:value-of select="version"/></td>
                            <td><xsl:value-of select="patchUpdate"/></td>
                            <td><xsl:value-of select="minorUpdate"/></td>
                            <td><xsl:value-of select="majorUpdate"/></td>
                        </tr>
                    </xsl:for-each>
                </table>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>