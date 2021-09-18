import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { NotifierService } from 'angular-notifier';
import { ActivatedRoute, Router } from '@angular/router';
import { TurLocale } from '../../../locale/model/locale.model';
import { TurLocaleService } from '../../../locale/service/locale.service';
import { TurSEInstance } from '../../../se/model/se-instance.model';
import { TurSEInstanceService } from '../../../se/service/se-instance.service';
import { TurConverseAgent } from '../../model/converse-agent.model';
import { TurConverseAgentService } from '../../service/converse-agent.service';

@Component({
  selector: 'converse-agent-page',
  templateUrl: './converse-agent-detail-page.component.html'
})
export class TurConverseAgentDetailPageComponent implements OnInit {
  private turConverseAgent: Observable<TurConverseAgent>;
  private turLocales: Observable<TurLocale[]>;
  private turSEInstances: Observable<TurSEInstance[]>;
  private newObject: boolean = false;

  constructor(
    private readonly notifier: NotifierService,
    private turConverseAgentService: TurConverseAgentService,
    private turLocaleService: TurLocaleService,
    private turSEInstanceService: TurSEInstanceService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    this.turLocales = turLocaleService.query();

    this.turSEInstances = turSEInstanceService.query();

    let id: string = this.activatedRoute.parent?.snapshot.paramMap.get('id') || "";

    this.newObject = (id.toLowerCase() === 'new');

    this.turConverseAgent = this.newObject ? this.turConverseAgentService.getStructure() : this.turConverseAgentService.get(id);
  }

  isNewObject(): boolean {
    return this.newObject;
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create agent" : "Update agent";
  }

  getTurConverseAgent(): Observable<TurConverseAgent> {
    return this.turConverseAgent;
  }

  getTurLocales(): Observable<TurLocale[]> {

    return this.turLocales;
  }

  getTurSEInstances(): Observable<TurSEInstance[]> {

    return this.turSEInstances;
  }
  ngOnInit(): void {
  }

  public save(_turConverseAgent: TurConverseAgent) {
    this.turConverseAgentService.save(_turConverseAgent, this.newObject).subscribe(
      (turConverseAgent: TurConverseAgent) => {
        let message: string = this.newObject ? " converse agent was created." : " converse agent was updated.";

        _turConverseAgent = turConverseAgent;

        this.notifier.notify("success", turConverseAgent.name.concat(message));

        this.router.navigate(['/converse/agent']);
      },
      response => {
        this.notifier.notify("error", "Converse agent was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }
}
