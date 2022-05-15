import nextcord
from nextcord import Interaction, slash_command
from nextcord.ext import commands, application_checks
from bot import client, Global_Report_Channel, Global_Log_Channel

global_report_channel = Global_Report_Channel
channel_id = Global_Log_Channel


class superkick(commands.Cog):
    def __init__(self, client):
        self.client = client

    @application_checks.has_permissions(ban_members=True)
    @slash_command(description="Bans a member from all guilds Venox is in")
    async def superkick(self, interaction: Interaction, member: nextcord.User, *, reason="No reason given"):
        # srnyx and chrizs id
        bot_owners = [242385234992037888, 273538684526264320]
        if interaction.user.id in bot_owners:
            member_id = member.id
            for g in client.guilds:
                if m := await g.fetch_member(member_id):
                    await m.kick(reason=reason)
                    await member.send(f"You have been kicked from all Venox Network servers for `{reason}`")
                    log_channel = await client.fetch_channel(channel_id)
                    await log_channel.send(f"`{member.name}` has been superkicked for `{reason}`")
                    await interaction.response.send_message("User was super kicked for `" + reason + "`")
        else:
            await interaction.response.send_message("You do not have permission to run this command")


def setup(client):
    client.add_cog(superkick(client))
